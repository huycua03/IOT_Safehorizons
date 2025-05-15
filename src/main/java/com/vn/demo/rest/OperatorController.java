package com.vn.demo.rest;

import com.vn.demo.dao.UserRepository;
import com.vn.demo.dao.ProfileRepository;
import com.vn.demo.dao.OperatorProfileRepository;
import com.vn.demo.dao.DeviceRepository;
import com.vn.demo.entity.User;
import com.vn.demo.entity.OperatorProfile;
import com.vn.demo.entity.Profile;
import com.vn.demo.entity.Device;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import java.net.InetSocketAddress;
import java.net.Socket;
import com.jcraft.jsch.JSchException;
import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("/operator")
@PreAuthorize("hasAuthority('OPERATOR')")
public class OperatorController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProfileRepository profileRepository;
    
    @Autowired
    private OperatorProfileRepository operatorProfileRepository;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Get current logged in operator
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> operatorOpt = userRepository.findByUser_name(username);
        
        if (operatorOpt.isPresent()) {
            User operator = operatorOpt.get();
            
            // Add operator info to model
            model.addAttribute("operator", operator);
            
            // Add assigned profiles if available
            if (operator.getOperatorProfiles() != null) {
                model.addAttribute("assignedProfiles", operator.getOperatorProfiles());
            }
            
            // You can add more relevant data for the operator dashboard here
        }
        
        return "operator-dashboard";
    }
    
    @GetMapping("/profile/view/{profileId}")
    public String viewProfile(@PathVariable("profileId") Integer profileId, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Lấy thông tin người dùng hiện tại (Operator)
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User operator = userRepository.findByUser_name(username).orElse(null);
            
            if (operator == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy thông tin người dùng!");
                return "redirect:/operator/dashboard";
            }
            
            // Tìm profile theo ID
            Profile profile = profileRepository.findById(profileId).orElse(null);
            
            if (profile == null) {
                model.addAttribute("errorMessage", "Không tìm thấy profile!");
                return "redirect:/operator/dashboard";
            }
            
            // Kiểm tra xem operator có được gán profile này không
            boolean isAssigned = false;
            if (operator.getOperatorProfiles() != null) {
                for (OperatorProfile op : operator.getOperatorProfiles()) {
                    if (op.getProfile().getProfileId() == profileId) {
                        isAssigned = true;
                        break;
                    }
                }
            }
            
            if (!isAssigned) {
                model.addAttribute("errorMessage", "Bạn không có quyền xem profile này!");
                return "redirect:/operator/dashboard";
            }
            
            // Lấy danh sách người dùng được gán profile này
            List<OperatorProfile> operatorProfiles = operatorProfileRepository.findByProfileId(profileId);
            
            model.addAttribute("profile", profile);
            model.addAttribute("operatorProfiles", operatorProfiles);
            return "operator-profile-view";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Lỗi khi xem profile: " + e.getMessage());
            return "redirect:/operator/dashboard";
        }
    }
    
    @GetMapping("/send-command")
    public String showSendCommandPage(Model model) {
        // Lấy thông tin người dùng hiện tại (Operator)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User operator = userRepository.findByUser_name(username).orElse(null);
        
        if (operator == null) {
            model.addAttribute("errorMessage", "Không tìm thấy thông tin người dùng!");
            return "redirect:/operator/dashboard";
        }
        
        // Lấy danh sách tất cả profile được gán cho operator này
        List<OperatorProfile> operatorProfiles = null;
        if (operator.getOperatorProfiles() != null) {
            operatorProfiles = operator.getOperatorProfiles();
        }
        
        // Lấy danh sách các thiết bị có sẵn
        List<Device> availableDevices = new ArrayList<>();
        
        if (operatorProfiles != null) {
            for (OperatorProfile op : operatorProfiles) {
                if (op.getProfile().getDeviceGroup() != null && op.getProfile().getDeviceGroup().getDevices() != null) {
                    availableDevices.addAll(op.getProfile().getDeviceGroup().getDevices());
                }
            }
        }
        
        // Danh sách các lệnh thông dụng
        List<String> commonCommands = new ArrayList<>();
        commonCommands.add("show running-config");
        commonCommands.add("configure terminal");
        commonCommands.add("interface GigabitEthernet0/1");
        commonCommands.add("switchport mode trunk");
        commonCommands.add("switchport trunk allowed vlan all");
        commonCommands.add("no shutdown");
        commonCommands.add("exit");
        
        model.addAttribute("devices", availableDevices);
        model.addAttribute("commonCommands", commonCommands);
        
        return "operator-send-command";
    }
    
    @PostMapping("/execute-command")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> executeCommand(@RequestParam Integer deviceId, 
                                                              @RequestParam String command,
                                                              @RequestParam(required = false) String username,
                                                              @RequestParam(required = false) String password) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn trước khi thực hiện lệnh
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = auth.getName();
            
            // Lấy thiết bị từ ID
            Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
            if (!deviceOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Không tìm thấy thiết bị với ID " + deviceId);
                return ResponseEntity.status(404).body(response);
            }
            
            Device device = deviceOpt.get();
            
            // Kiểm tra thiết bị có thuộc về device group mà người dùng được phân quyền không
            boolean hasAccess = checkUserDeviceAccess(currentUsername, device);
            if (!hasAccess) {
                response.put("success", false);
                response.put("message", "Bạn không có quyền thực hiện lệnh trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Nếu device.ipAddress là null hoặc rỗng, thử tìm IP thông qua docker
            if (device.getIpAddress() == null || device.getIpAddress().trim().isEmpty()) {
                String containerName = device.getDeviceName();
                if (containerName != null && !containerName.trim().isEmpty()) {
                    String containerIp = getContainerIpByName(containerName);
                    if (containerIp != null && !containerIp.trim().isEmpty()) {
                        device.setIpAddress(containerIp);
                        deviceRepository.save(device);
                    }
                }
            }
            
            // Nếu vẫn không có IP, trả về lỗi
            if (device.getIpAddress() == null || device.getIpAddress().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Không tìm thấy địa chỉ IP cho thiết bị này");
                return ResponseEntity.status(400).body(response);
            }
            
            // Thực hiện kết nối SSH và gửi lệnh
            String result = executeSSHCommand(device.getIpAddress(), 
                                             username != null ? username : "root", 
                                             password != null ? password : "password", 
                                             command);
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("command", command);
            response.put("result", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi thực hiện lệnh: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy địa chỉ IP của container dựa vào tên
     */
    private String getContainerIpByName(String containerName) {
        try {
            Process process = Runtime.getRuntime().exec("docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' " + containerName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String ip = reader.readLine();
            process.waitFor();
            reader.close();
            
            if (ip != null) {
                ip = ip.replace("'", "").trim();
                System.out.println("Container " + containerName + " has IP: " + ip);
                return ip.isEmpty() ? null : ip;
            }
        } catch (Exception e) {
            System.out.println("Error getting IP for container " + containerName + ": " + e.getMessage());
        }
        return null;
    }

    private String executeSSHCommand(String host, String username, String password, String command) {
        StringBuilder output = new StringBuilder();
        
        try {
            // Thử kết nối host ban đầu
            boolean connected = false;
            
            try (Socket socket = new Socket()) {
                output.append("Đang thử kết nối đến " + host + ":2222...\n");
                socket.connect(new InetSocketAddress(host, 2222), 3000);
                connected = true;
                output.append("Kết nối thành công đến " + host + ":2222\n");
            } catch (IOException e) {
                output.append("Không thể kết nối đến ").append(host).append(":2222 - ").append(e.getMessage()).append("\n");
                
                // Thử lấy IP container theo tên (nếu host có thể là tên container)
                String containerIp = getContainerIpByName(host);
                if (containerIp != null && !containerIp.isEmpty()) {
                    output.append("Tìm thấy IP của container " + host + ": " + containerIp + "\n");
                    try (Socket socket = new Socket()) {
                        output.append("Thử kết nối đến " + containerIp + ":2222...\n");
                        socket.connect(new InetSocketAddress(containerIp, 2222), 3000);
                        connected = true;
                        host = containerIp; // Sử dụng IP tìm được
                        output.append("Kết nối thành công đến " + host + ":2222\n");
                    } catch (IOException ex) {
                        output.append("Không thể kết nối đến " + containerIp + ":2222 - " + ex.getMessage() + "\n");
                    }
                }
                
                // Nếu vẫn không kết nối được, thử localhost
                if (!connected) {
                    String[] backupHosts = {"localhost", "127.0.0.1"};
                    for (String backupHost : backupHosts) {
                        try (Socket socket = new Socket()) {
                            output.append("Thử kết nối đến " + backupHost + ":2222...\n");
                            socket.connect(new InetSocketAddress(backupHost, 2222), 3000);
                            connected = true;
                            host = backupHost;
                            output.append("Kết nối thành công đến " + host + ":2222\n");
                            break;
                        } catch (IOException ex) {
                            output.append("Không thể kết nối đến " + backupHost + ":2222 - " + ex.getMessage() + "\n");
                        }
                    }
                }
            }
            
            if (!connected) {
                output.append("\nKhông thể kết nối đến bất kỳ host nào.\n");
                output.append("Nguyên nhân có thể:\n");
                output.append("1. Container không có SSH server được cài đặt\n");
                output.append("2. SSH server không chạy hoặc không lắng nghe trên cổng 2222\n");
                output.append("3. Firewall đang chặn kết nối\n\n");
                output.append("Giải pháp:\n");
                output.append("- Đảm bảo container có cài đặt SSH server (openssh-server)\n");
                output.append("- Kiểm tra SSH server đang chạy và cấu hình đúng\n");
                output.append("- Kiểm tra cấu hình port mapping Docker\n");
                return output.toString();
            }

            output.append("Đang kết nối SSH đến ").append(host).append(":2222...\n");
            
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, 2222);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            
            try {
                session.connect(5000); // Giảm timeout xuống 5 giây
                output.append("Kết nối SSH thành công!\n\n");
            } catch (JSchException e) {
                output.append("Không thể xác thực SSH: ").append(e.getMessage()).append("\n");
                output.append("Vui lòng kiểm tra lại username và password.\n");
                return output.toString();
            }
            
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            
            // Lấy input và error stream
            OutputStream outStream = channel.getOutputStream();
            channel.setInputStream(null);
            ((ChannelExec) channel).setErrStream(System.err);
            
            java.io.InputStream in = channel.getInputStream();
            
            // Kết nối kênh
            output.append("Executing command: ").append(command).append("\n");
            channel.connect();
            
            // Đọc kết quả
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    String result = new String(tmp, 0, i);
                    output.append(result);
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    output.append("\nExit status: ").append(channel.getExitStatus()).append("\n");
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    output.append("Error while reading command output: ").append(e.getMessage()).append("\n");
                }
            }
            
            // Đóng kết nối
            channel.disconnect();
            session.disconnect();
            output.append("Disconnected from ").append(host).append("\n");
            
        } catch (Exception e) {
            output.append("Error during SSH execution: ").append(e.getMessage()).append("\n");
        }
        
        return output.toString();
    }

    /**
     * Kiểm tra người dùng có quyền truy cập thiết bị không
     */
    private boolean checkUserDeviceAccess(String username, Device device) {
        try {
            // Lấy thông tin người dùng
            User operator = userRepository.findByUser_name(username).orElse(null);
            if (operator == null) {
                return false;
            }
            
            // Kiểm tra quyền truy cập thiết bị
            boolean hasAccess = false;
            for (OperatorProfile op : operator.getOperatorProfiles()) {
                if (op.getProfile().getDeviceGroup() != null && 
                    op.getProfile().getDeviceGroup().getDevices() != null) {
                    for (Device d : op.getProfile().getDeviceGroup().getDevices()) {
                        if (d.getDeviceId() == device.getDeviceId()) {
                            hasAccess = true;
                            break;
                        }
                    }
                }
                if (hasAccess) break;
            }
            
            return hasAccess;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
} 