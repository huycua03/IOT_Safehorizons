package com.vn.demo.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.vn.demo.dao.CommandListRepository;
import com.vn.demo.dao.CommandRepository;
import com.vn.demo.dao.DeviceGroupRepository;
import com.vn.demo.dao.DeviceRepository;
import com.vn.demo.dao.OperatorProfileRepository;
import com.vn.demo.dao.ProfileRepository;
import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.Device;
import com.vn.demo.entity.DeviceGroup;
import com.vn.demo.entity.OperatorProfile;
import com.vn.demo.entity.Profile;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    
    @Autowired
    private CommandRepository commandRepository;
    
    @Autowired
    private CommandListRepository commandListRepository;
    
    @Autowired
    private com.vn.demo.dao.SessionRepository sessionRepository;
    
    // Lưu trữ các phiên SSH đang hoạt động
    private static final ConcurrentHashMap<String, SSHSession> activeSSHSessions = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<String, Map<String, Object>> SSH_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, WebSocketSession> SSH_WEBSOCKET_SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final ConcurrentHashMap<String, OutputStream> outputStreams = new ConcurrentHashMap<>();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Class để quản lý session SSH
     */
    private static class SSHSession {
        private final Session session;
        private final ChannelShell channel;
        private final InputStream inputStream;
        private final PrintStream outputStream;
        private final Thread readerThread;
        private final WebSocketSession webSocketSession;
        private boolean active = true;
        
        public SSHSession(Session session, ChannelShell channel, 
                          InputStream inputStream, PrintStream outputStream,
                          WebSocketSession webSocketSession) {
            this.session = session;
            this.channel = channel;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.webSocketSession = webSocketSession;
            
            // Tạo thread để đọc output từ shell liên tục
            this.readerThread = new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while (active && (bytesRead = inputStream.read(buffer)) != -1) {
                        if (bytesRead > 0) {
                            String output = new String(buffer, 0, bytesRead);
                            sendToWebSocket(output);
                        }
                    }
                } catch (Exception e) {
                    try {
                        sendToWebSocket("\r\nLỗi kết nối: " + e.getMessage() + "\r\n");
                    } catch (Exception ex) {
                        System.err.println("Lỗi khi gửi thông báo lỗi đến websocket: " + ex.getMessage());
                    }
                }
            });
            
            readerThread.start();
        }
        
        public void sendCommand(String command) {
            try {
                // Gửi dữ liệu dưới dạng bytes thay vì sử dụng println
                byte[] data = command.getBytes();
                outputStream.write(data);
                outputStream.flush();
            } catch (Exception e) {
                System.err.println("Lỗi khi gửi lệnh: " + e.getMessage());
            }
        }
        
        public void close() {
            active = false;
            try {
                if (readerThread != null && readerThread.isAlive()) {
                    readerThread.interrupt();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (channel != null) {
                    channel.disconnect();
                }
                if (session != null) {
                    session.disconnect();
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi đóng phiên SSH: " + e.getMessage());
            }
        }
        
        private void sendToWebSocket(String message) throws Exception {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                webSocketSession.sendMessage(new TextMessage(message));
            }
        }
    }
    
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
    public String redirectToTerminal() {
        return "redirect:/operator/terminal";
    }
    
    @GetMapping("/file-editor")
    public String showFileEditorPage(Model model) {
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
        
        model.addAttribute("devices", availableDevices);
        return "operator-file-editor";
    }
    
    @GetMapping("/terminal")
    public String showTerminalPage(Model model) {
        // Kiểm tra quyền hạn và lấy danh sách thiết bị
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        List<Device> availableDevices = new ArrayList<>();
        
        Optional<User> userOpt = userRepository.findByUser_name(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Lấy danh sách profiles của user
            List<OperatorProfile> operatorProfiles = operatorProfileRepository.findByUserId(user.getUser_id());
            
            // Lấy danh sách thiết bị từ các device groups liên quan đến profiles
            for (OperatorProfile opProfile : operatorProfiles) {
                Profile profile = opProfile.getProfile();
                if (profile != null && profile.getDeviceGroup() != null) {
                    DeviceGroup deviceGroup = profile.getDeviceGroup();
                    // Lấy danh sách thiết bị trong group
                    List<Device> devices = deviceRepository.findByDeviceGroupId(deviceGroup.getGroupId());
                    for (Device device : devices) {
                        if (!availableDevices.contains(device)) {
                            availableDevices.add(device);
                        }
                    }
                }
            }
        }
        
        model.addAttribute("devices", availableDevices);
        
        return "operator-terminal";
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
     * Lấy địa chỉ IP từ container dựa vào tên
     * @param containerName tên container cần lấy IP
     * @return địa chỉ IP của container, hoặc null nếu không tìm thấy
     */
    private String getContainerIpByName(String containerName) {
        try {
            // Phương pháp 1: Sử dụng lệnh Docker để lấy IP container
            String command = "docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' " + containerName;
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String ip = reader.readLine();
            process.waitFor();
            reader.close();
            
            // Nếu IP không rỗng và không chứa lỗi, trả về IP
            if (ip != null && !ip.isEmpty() && !ip.contains("Error") && !ip.equals("''")) {
                // Loại bỏ dấu nháy đơn nếu có
                return ip.replace("'", "").trim();
            }
            
            // Phương pháp 2: Phân tích kết quả JSON của Docker inspect
            command = "docker inspect " + containerName;
            process = Runtime.getRuntime().exec(command);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            process.waitFor();
            reader.close();
            
            // Tìm IP trong output JSON với regex đơn giản
            Pattern pattern = Pattern.compile("\"IPAddress\":\\s*\"([0-9.]+)\"");
            Matcher matcher = pattern.matcher(output.toString());
            
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            // Phương pháp cuối cùng: sử dụng localhost nếu container chạy trên cùng máy
            return "localhost";
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Thực thi lệnh SSH và trả về kết quả
     * @param host địa chỉ máy chủ
     * @param username tên đăng nhập
     * @param password mật khẩu
     * @param command lệnh cần thực thi
     * @return kết quả thực thi lệnh
     */
    private String executeSSHCommand(String host, String username, String password, String command) {
        StringBuilder output = new StringBuilder();
        
        try {
            // Cổng SSH mặc định cho Docker
            int sshPort = 2222;
            
            // Kiểm tra kết nối trước khi thực hiện lệnh SSH
            boolean connected = tryConnectToHost(host, sshPort, output);
            
            if (!connected) {
                // Thử kết nối với các phương pháp thay thế
                connected = tryAlternativeHostConnections(host, sshPort, output);
            }
            
            if (!connected) {
                output.append("\nKhông thể kết nối đến bất kỳ host nào.\n");
                output.append("Nguyên nhân có thể:\n");
                output.append("1. Container không có SSH server được cài đặt\n");
                output.append("2. SSH server không chạy hoặc không lắng nghe trên cổng ").append(sshPort).append("\n");
                output.append("3. Firewall đang chặn kết nối\n\n");
                output.append("Giải pháp:\n");
                output.append("- Đảm bảo container có cài đặt SSH server (openssh-server)\n");
                output.append("- Kiểm tra SSH server đang chạy và cấu hình đúng\n");
                output.append("- Kiểm tra cấu hình port mapping Docker\n");
                return output.toString();
            }

            output.append("Đang kết nối SSH đến ").append(host).append(":").append(sshPort).append("...\n");
            
            // Thực hiện kết nối SSH và gửi lệnh
            return executeSSHCommandWithJSch(host, username, password, command, sshPort, output);
            
        } catch (Exception e) {
            output.append("Lỗi trong quá trình SSH: ").append(e.getMessage()).append("\n");
        }
        
        return output.toString();
    }
    
    /**
     * Thử kết nối đến host
     */
    private boolean tryConnectToHost(String host, int port, StringBuilder log) {
        try (Socket socket = new Socket()) {
            log.append("Đang thử kết nối đến ").append(host).append(":").append(port).append("...\n");
            socket.connect(new InetSocketAddress(host, port), 3000);
            log.append("Kết nối thành công đến ").append(host).append(":").append(port).append("\n");
            return true;
        } catch (IOException e) {
            log.append("Không thể kết nối đến ").append(host).append(":").append(port).append(" - ").append(e.getMessage()).append("\n");
            return false;
        }
    }
    
    /**
     * Thử kết nối tới host với các phương pháp thay thế
     */
    private boolean tryAlternativeHostConnections(String originalHost, int port, StringBuilder log) {
        // Thử kết nối qua localhost
        if (tryConnectToHost("localhost", port, log)) {
            return true;
        }
        
        // Thử lấy IP container theo tên
        String containerIp = getContainerIpByName(originalHost);
        if (containerIp != null && !containerIp.isEmpty()) {
            log.append("Tìm thấy IP của container ").append(originalHost).append(": ").append(containerIp).append("\n");
            if (tryConnectToHost(containerIp, port, log)) {
                return true;
            }
        }
        
        // Thử kết nối qua 127.0.0.1
        return tryConnectToHost("127.0.0.1", port, log);
    }
    
    /**
     * Thực thi lệnh SSH sử dụng thư viện JSch
     */
    private String executeSSHCommandWithJSch(String host, String username, String password, String command, int sshPort, StringBuilder output) {
        try {
            // Tạo kết nối SSH
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, sshPort);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            
            try {
                // Kết nối đến SSH server
                session.connect(5000); // Giảm timeout xuống 5 giây
                output.append("Kết nối SSH thành công!\n\n");
            } catch (JSchException e) {
                output.append("Không thể xác thực SSH: ").append(e.getMessage()).append("\n");
                output.append("Vui lòng kiểm tra lại username và password.\n");
                return output.toString();
            }
            
            // Tạo kênh SSH kiểu exec để thực hiện lệnh
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            
            // Lấy input và error stream
            channel.setInputStream(null);
            ((ChannelExec) channel).setErrStream(System.err);
            
            InputStream in = channel.getInputStream();
            
            // Kết nối kênh
            output.append("Đang thực thi lệnh: ").append(command).append("\n");
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
                    output.append("\nMã trạng thái: ").append(channel.getExitStatus()).append("\n");
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    output.append("Lỗi khi đọc kết quả lệnh: ").append(e.getMessage()).append("\n");
                }
            }
            
            // Đóng kết nối
            channel.disconnect();
            session.disconnect();
            output.append("Đã ngắt kết nối từ ").append(host).append("\n");
            
        } catch (Exception e) {
            output.append("Lỗi trong quá trình SSH: ").append(e.getMessage()).append("\n");
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

    @PostMapping("/create-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFile(
            @RequestParam Integer deviceId,
            @RequestParam String filename,
            @RequestParam String content,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Chuẩn bị lệnh để tạo file
            String escapedContent = escapeShellArg(content);
            String command = "echo " + escapedContent + " > " + filename;
            
            // Thực hiện kết nối SSH và gửi lệnh
            String result = executeSSHCommand(device.getIpAddress(), 
                                           username != null ? username : "root", 
                                           password != null ? password : "password", 
                                           command);
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("filename", filename);
            response.put("result", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi tạo file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/edit-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editFile(
            @RequestParam Integer deviceId,
            @RequestParam String filename,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Đọc nội dung file
            String command = "cat " + filename;
            
            // Thực hiện kết nối SSH và gửi lệnh
            String result = executeSSHCommand(device.getIpAddress(), 
                                           username != null ? username : "root", 
                                           password != null ? password : "password", 
                                           command);
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("filename", filename);
            response.put("content", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi đọc file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/save-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveFile(
            @RequestParam Integer deviceId,
            @RequestParam String filename,
            @RequestParam String content,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Chuẩn bị lệnh để lưu file
            String escapedContent = escapeShellArg(content);
            String command = "echo " + escapedContent + " > " + filename;
            
            // Thực hiện kết nối SSH và gửi lệnh
            String result = executeSSHCommand(device.getIpAddress(), 
                                           username != null ? username : "root", 
                                           password != null ? password : "password", 
                                           command);
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("filename", filename);
            response.put("result", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi lưu file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/run-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runFile(
            @RequestParam Integer deviceId,
            @RequestParam String filename,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Chuẩn bị lệnh để chạy file dựa vào phần mở rộng
            String command = "";
            if (filename.toLowerCase().endsWith(".py")) {
                command = "python " + filename;
            } else if (filename.toLowerCase().endsWith(".sh")) {
                command = "bash " + filename;
            } else if (filename.toLowerCase().endsWith(".js")) {
                command = "node " + filename;
            } else if (filename.toLowerCase().endsWith(".txt")) {
                command = "cat " + filename;
            } else {
                // Thử chạy file như một script thực thi
                command = "chmod +x " + filename + " && ./" + filename;
            }
            
            // Thực hiện kết nối SSH và gửi lệnh
            String result = executeSSHCommand(device.getIpAddress(), 
                                           username != null ? username : "root", 
                                           password != null ? password : "password", 
                                           command);
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("filename", filename);
            response.put("result", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi chạy file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/list-files")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listFiles(
            @RequestParam Integer deviceId,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Sử dụng đường dẫn hiện tại nếu không có đường dẫn được chỉ định
            String targetPath = path != null && !path.trim().isEmpty() ? path : ".";
            String command = "ls -la " + targetPath;
            
            // Thực hiện kết nối SSH và gửi lệnh
            String result = executeSSHCommand(device.getIpAddress(), 
                                           username != null ? username : "root", 
                                           password != null ? password : "password", 
                                           command);
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("path", targetPath);
            response.put("result", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi liệt kê file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Escape shell arguments để tránh command injection
     */
    private String escapeShellArg(String arg) {
        // Escape single quotes by replacing them with '\'' (close quote, escaped quote, open quote)
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    /**
     * Tạo, chỉnh sửa, lưu và chạy các loại file trong container
     */
    @PostMapping("/edit-with-tool")
    @ResponseBody
    public Map<String, Object> editWithTool(@RequestParam("deviceId") String deviceId,
                                          @RequestParam("tool") String tool) {
        Map<String, Object> result = new HashMap<>();
        // Trả về thông báo là chức năng không được hỗ trợ
        result.put("success", false);
        result.put("message", "Chức năng này đã bị vô hiệu hóa. Vui lòng sử dụng trình soạn thảo tích hợp.");
        return result;
    }

    @PostMapping("/append-to-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> appendToFile(
            @RequestParam Integer deviceId,
            @RequestParam String filename,
            @RequestParam String content,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Chuẩn bị lệnh để thêm vào file
            String escapedContent = escapeShellArg(content);
            String command = "echo " + escapedContent + " >> " + filename;
            
            // Thực hiện kết nối SSH và gửi lệnh
            String result = executeSSHCommand(device.getIpAddress(), 
                                           username != null ? username : "root", 
                                           password != null ? password : "password", 
                                           command);
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("filename", filename);
            response.put("result", result);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi thêm vào file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-tools")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkTools(
            @RequestParam Integer deviceId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Kiểm tra các công cụ có sẵn
            String[] tools = {"nano", "vi", "vim", "python", "python3", "bash", "node", "npm"};
            Map<String, Boolean> availableTools = new HashMap<>();
            
            for (String tool : tools) {
                String checkCommand = "which " + tool + " > /dev/null 2>&1 && echo 'installed' || echo 'not installed'";
                String result = executeSSHCommand(device.getIpAddress(), 
                                               username != null ? username : "root", 
                                               password != null ? password : "password", 
                                               checkCommand);
                
                availableTools.put(tool, result.contains("installed"));
            }
            
            response.put("success", true);
            response.put("deviceId", deviceId);
            response.put("availableTools", availableTools);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi kiểm tra công cụ: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Tạo kết nối SSH shell mới và trả về ID phiên
     */
    @PostMapping("/open-terminal")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> openTerminal(
            @RequestParam Integer deviceId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String sessionId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Kiểm tra quyền hạn
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
                response.put("message", "Bạn không có quyền thực hiện thao tác trên thiết bị này");
                return ResponseEntity.status(403).body(response);
            }
            
            // Kiểm tra nếu có phiên đã tồn tại với ID này thì đóng và tạo mới
            if (sessionId != null && activeSSHSessions.containsKey(sessionId)) {
                SSHSession existingSession = activeSSHSessions.get(sessionId);
                existingSession.close();
                activeSSHSessions.remove(sessionId);
            }
            
            // Tạo ID phiên mới nếu chưa có
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = "ssh-" + System.currentTimeMillis() + "-" + deviceId;
            }
            
            // Lưu vào cơ sở dữ liệu theo entity Session
            User operator = userRepository.findByUser_name(currentUsername).orElse(null);
            if (operator != null) {
                // Tìm supervisor được phân quyền
                User supervisor = findSupervisorForOperator(operator);
                
                // Lưu session vào database
                com.vn.demo.entity.Session dbSession = new com.vn.demo.entity.Session();
                dbSession.setOperator(operator);
                dbSession.setDevice(device);
                dbSession.setSupervisor(supervisor != null ? supervisor : operator);  // Fallback nếu không tìm thấy supervisor
                dbSession.setStartTime(java.time.LocalDateTime.now());
                dbSession.setStatus("ACTIVE");
                
                // Lưu session entity vào database
                sessionRepository.save(dbSession);
                
                // Lưu ID của entity session vào trong thông tin phiên SSH
                Map<String, Object> sessionInfo = new HashMap<>();
                sessionInfo.put("dbSessionId", dbSession.getSessionId());
                sessionInfo.put("operatorId", operator.getUser_id());
                sessionInfo.put("operatorName", operator.getUser_name());
                sessionInfo.put("deviceId", device.getDeviceId());
                sessionInfo.put("deviceName", device.getDeviceName());
                sessionInfo.put("startTime", System.currentTimeMillis());
                sessionInfo.put("status", "ACTIVE");
                
                SSH_SESSIONS.put(sessionId, sessionInfo);
            }
            
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("deviceId", deviceId);
            response.put("deviceName", device.getDeviceName());
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi tạo terminal: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Đóng kết nối SSH shell
     */
    @PostMapping("/close-terminal")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> closeTerminal(
            @RequestParam String sessionId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (activeSSHSessions.containsKey(sessionId)) {
                SSHSession sshSession = activeSSHSessions.get(sessionId);
                sshSession.close();
                activeSSHSessions.remove(sessionId);
                
                response.put("success", true);
                response.put("message", "Đã đóng kết nối terminal");
            } else {
                response.put("success", false);
                response.put("message", "Không tìm thấy phiên terminal với ID " + sessionId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi đóng terminal: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Thiết lập phiên shell SSH từ WebSocket với cổng SSH tùy chọn
     */
    public void setupSSHShellConnection(String sessionId, String hostAddress, String username, String password, WebSocketSession webSocketSession, int sshPort) {
        // Lưu phiên vào Map
        SSH_SESSIONS.put(sessionId, new HashMap<>());
        SSH_WEBSOCKET_SESSIONS.put(sessionId, webSocketSession);
        
        // Kết nối SSH trong thread riêng biệt
        executor.execute(() -> {
            try {
                // Gửi thông báo bắt đầu kết nối
                sendWebSocketMessage(webSocketSession, "info", "Đang kết nối WebSocket đến ws://localhost:8080/ssh-terminal...");
                sendWebSocketMessage(webSocketSession, "info", "Phiên đã được tạo: " + sessionId);
                
                // Biến lưu trữ địa chỉ host cuối cùng sẽ được sử dụng
                final String[] effectiveHost = {hostAddress};
                
                // Kiểm tra kết nối với host
                boolean canConnect = checkHostConnectivity(effectiveHost, sshPort, webSocketSession);
                
                if (!canConnect) {
                    sendWebSocketMessage(webSocketSession, "error", "Không thể thiết lập kết nối SSH đến thiết bị này với bất kỳ phương pháp nào.");
                    sendWebSocketMessage(webSocketSession, "error", "Vui lòng kiểm tra lại thiết bị và đảm bảo SSH server đang chạy và kiểm tra lại port SSH (hiện tại: " + sshPort + ")");
                    return;
                }
                
                try {
                    sendWebSocketMessage(webSocketSession, "info", "Đang thiết lập kết nối SSH...");
                    // Sử dụng thư viện JSch để kết nối SSH
                    JSch jsch = new JSch();
                    Session session = jsch.getSession(username, effectiveHost[0], sshPort);
                    session.setPassword(password);
                    
                    // Tắt kiểm tra host key để đơn giản hóa
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    
                    // Timeout cho việc kết nối
                    session.connect(10000);
                    sendWebSocketMessage(webSocketSession, "info", "Đã kết nối đến: " + effectiveHost[0]);
                    
                    // Mở một kênh shell
                    Channel channel = session.openChannel("shell");
                    
                    // Lấy luồng input/output
                    InputStream inputStream = channel.getInputStream();
                    outputStreams.put(sessionId, channel.getOutputStream());
                    
                    // Kết nối kênh
                    channel.connect(5000);
                    sendWebSocketMessage(webSocketSession, "info", "Đã thực hiện thành công! Đang mở shell...");
                    
                    // Lưu thông tin phiên
                    Map<String, Object> sessionInfo = SSH_SESSIONS.get(sessionId);
                    sessionInfo.put("jschSession", session);
                    sessionInfo.put("channel", channel);
                    
                    // Đọc dữ liệu từ shell trong một thread riêng biệt
                    startShellReaderThread(inputStream, webSocketSession, sessionId);
                } catch (JSchException | IOException e) {
                    System.out.println("Lỗi khi thiết lập kết nối SSH: " + e.getMessage());
                    sendWebSocketMessage(webSocketSession, "error", "Lỗi kết nối SSH: " + e.getMessage());
                }
            } catch (Exception e) {
                System.out.println("Lỗi không xác định: " + e.getMessage());
                try {
                    sendWebSocketMessage(webSocketSession, "error", "Lỗi không xác định: " + e.getMessage());
                } catch (Exception ex) {
                    // Bỏ qua
                }
            }
        });
    }
    
    /**
     * Kiểm tra khả năng kết nối đến host với nhiều phương pháp khác nhau
     */
    private boolean checkHostConnectivity(String[] effectiveHost, int sshPort, WebSocketSession webSocketSession) {
        boolean canConnect = false;
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(effectiveHost[0], sshPort), 3000);
            socket.close();
            canConnect = true;
            sendWebSocketMessage(webSocketSession, "info", "Kiểm tra kết nối thành công: " + effectiveHost[0] + ":" + sshPort);
        } catch (IOException e) {
            System.out.println("Không thể kết nối trực tiếp đến " + effectiveHost[0] + ":" + sshPort + " - " + e.getMessage());
            sendWebSocketMessage(webSocketSession, "info", "Không thể kết nối trực tiếp. Đang thử phương pháp thay thế...");
            
            // Thử nhiều phương pháp kết nối khác nhau
            canConnect = tryAlternativeConnections(effectiveHost, sshPort, webSocketSession);
        }
        return canConnect;
    }
    
    /**
     * Thử các phương pháp kết nối thay thế
     */
    private boolean tryAlternativeConnections(String[] effectiveHost, int sshPort, WebSocketSession webSocketSession) {
        // Thử kết nối qua localhost
        if (tryConnectionWebSocket("localhost", sshPort, effectiveHost, webSocketSession)) {
            return true;
        }
        
        // Thử lấy IP container theo tên (nếu host có thể là tên container)
        String containerIp = getContainerIpByName(effectiveHost[0]);
        if (containerIp != null && !containerIp.isEmpty()) {
            System.out.println("Tìm thấy IP của container " + effectiveHost[0] + ": " + containerIp);
            sendWebSocketMessage(webSocketSession, "info", "Tìm thấy IP của container: " + containerIp);
            
            if (tryConnectionWebSocket(containerIp, sshPort, effectiveHost, webSocketSession)) {
                return true;
            }
        }
        
        // Cuối cùng, thử kết nối đến 127.0.0.1
        return tryConnectionWebSocket("127.0.0.1", sshPort, effectiveHost, webSocketSession);
    }
    
    /**
     * Thử kết nối đến một host cụ thể khi sử dụng WebSocket
     */
    private boolean tryConnectionWebSocket(String host, int sshPort, String[] effectiveHost, WebSocketSession webSocketSession) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, sshPort), 3000);
            socket.close();
            effectiveHost[0] = host; // Cập nhật host hiệu quả
            sendWebSocketMessage(webSocketSession, "info", "Kết nối thành công đến: " + effectiveHost[0] + ":" + sshPort);
            return true;
        } catch (IOException e) {
            System.out.println("Không thể kết nối đến " + host + ":" + sshPort + " - " + e.getMessage());
            sendWebSocketMessage(webSocketSession, "error", "Không thể kết nối đến: " + host + ":" + sshPort);
            return false;
        }
    }
    
    /**
     * Bắt đầu thread đọc dữ liệu từ shell
     */
    private void startShellReaderThread(InputStream inputStream, WebSocketSession webSocketSession, String sessionId) {
        executor.execute(() -> {
            byte[] buffer = new byte[1024];
            try {
                int i;
                // Đọc cho đến khi kết nối đóng
                while ((i = inputStream.read(buffer)) != -1) {
                    if (i > 0 && webSocketSession.isOpen()) {
                        // Chuyển dữ liệu nhận được từ shell về client
                        String output = new String(buffer, 0, i);
                        webSocketSession.sendMessage(new TextMessage(output));
                    }
                }
            } catch (IOException e) {
                System.out.println("Lỗi khi đọc từ phiên SSH: " + e.getMessage());
                try {
                    sendWebSocketMessage(webSocketSession, "error", "Kết nối bị ngắt: " + e.getMessage());
                } catch (Exception ex) {
                    // Bỏ qua
                }
            } finally {
                closeSSHSession(sessionId);
            }
        });
    }
    
    /**
     * Thiết lập phiên shell SSH từ WebSocket với cổng mặc định 2222
     */
    public void setupSSHShellConnection(String sessionId, String host, String username, String password, WebSocketSession webSocketSession) {
        setupSSHShellConnection(sessionId, host, username, password, webSocketSession, 2222);
    }

    private void sendWebSocketMessage(WebSocketSession session, String type, String message) {
        try {
            if (session != null && session.isOpen()) {
                Map<String, String> data = new HashMap<>();
                data.put("type", type);
                data.put("message", message);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            }
        } catch (Exception e) {
            System.out.println("Lỗi khi gửi thông điệp WebSocket: " + e.getMessage());
        }
    }

    /**
     * Gửi lệnh tới phiên SSH đang hoạt động
     */
    public void sendCommandToSSHSession(String sessionId, String command) {
        try {
            OutputStream outputStream = outputStreams.get(sessionId);
            if (outputStream != null) {
                // Đảm bảo lệnh được gửi đúng định dạng
                byte[] commandBytes = command.getBytes();
                outputStream.write(commandBytes);
                outputStream.flush();
            } else {
                System.out.println("Không tìm thấy output stream cho phiên: " + sessionId);
                
                // Kiểm tra xem phiên có tồn tại không
                Map<String, Object> sessionInfo = SSH_SESSIONS.get(sessionId);
                if (sessionInfo == null) {
                    System.out.println("Phiên SSH không tồn tại: " + sessionId);
                } else {
                    System.out.println("Phiên SSH tồn tại nhưng không có outputStream: " + sessionId);
                    
                    // Thử lấy lại channel và tạo outputStream mới
                    Channel channel = (Channel) sessionInfo.get("channel");
                    if (channel != null && channel.isConnected()) {
                        System.out.println("Đang thử tạo lại outputStream cho phiên: " + sessionId);
                        outputStreams.put(sessionId, channel.getOutputStream());
                        
                        // Thử gửi lại lệnh
                        OutputStream newOutputStream = outputStreams.get(sessionId);
                        if (newOutputStream != null) {
                            newOutputStream.write(command.getBytes());
                            newOutputStream.flush();
                            System.out.println("Đã gửi lại lệnh thành công sau khi tạo lại outputStream");
                        } else {
                            System.out.println("Không thể tạo lại outputStream");
                        }
                    } else {
                        System.out.println("Channel không tồn tại hoặc đã đóng");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Lỗi khi gửi lệnh đến phiên SSH: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Đóng phiên SSH và dọn dẹp tài nguyên
     */
    public void closeSSHSession(String sessionId) {
        try {
            // Lấy thông tin phiên từ map
            Map<String, Object> sessionInfo = SSH_SESSIONS.get(sessionId);
            if (sessionInfo != null) {
                // Đóng kênh nếu có
                Channel channel = (Channel) sessionInfo.get("channel");
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                }
                
                // Đóng session SSH nếu có
                Session session = (Session) sessionInfo.get("jschSession");
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
                
                // Đóng output stream nếu có
                OutputStream outputStream = outputStreams.get(sessionId);
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        // Bỏ qua
                    }
                    outputStreams.remove(sessionId);
                }
                
                // Cập nhật trạng thái session trong database nếu có
                if (sessionInfo.containsKey("dbSessionId")) {
                    try {
                        Integer dbSessionId = (Integer) sessionInfo.get("dbSessionId");
                        if (dbSessionId != null) {
                            Optional<com.vn.demo.entity.Session> dbSessionOpt = sessionRepository.findById(dbSessionId);
                            if (dbSessionOpt.isPresent()) {
                                com.vn.demo.entity.Session dbSession = dbSessionOpt.get();
                                dbSession.setEndTime(java.time.LocalDateTime.now());
                                dbSession.setStatus("CLOSED");
                                sessionRepository.save(dbSession);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Lỗi khi cập nhật session trong database: " + e.getMessage());
                    }
                }
                
                // Xóa phiên khỏi map
                SSH_SESSIONS.remove(sessionId);
                
                // Gửi thông báo đến client thông qua WebSocket nếu session vẫn còn mở
                WebSocketSession webSocketSession = SSH_WEBSOCKET_SESSIONS.get(sessionId);
                if (webSocketSession != null && webSocketSession.isOpen()) {
                    sendWebSocketMessage(webSocketSession, "info", "Phiên SSH đã đóng");
                    SSH_WEBSOCKET_SESSIONS.remove(sessionId);
                }
            }
        } catch (Exception e) {
            System.out.println("Lỗi khi đóng phiên SSH: " + e.getMessage());
        }
    }

    /**
     * Tìm supervisor được phân quyền cho một operator
     */
    private User findSupervisorForOperator(User operator) {
        try {
            // Logic tìm supervisor được phân quyền cho operator này
            // Trong trường hợp này, chúng ta có thể trả về một supervisor bất kỳ
            return userRepository.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains("ROLE_SUPERVISOR"))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            System.out.println("Lỗi khi tìm supervisor: " + e.getMessage());
            return null;
        }
    }

    /**
     * Phương thức quét và cập nhật thông tin container
     * Sử dụng để phát hiện các container Docker đang chạy và cập nhật IP
     */
    @PostMapping("/scan-containers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> scanAndUpdateDockerContainers() {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> containerInfoList = new ArrayList<>();
        
        try {
            // Kiểm tra quyền hạn
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = auth.getName();
            User operator = userRepository.findByUser_name(currentUsername).orElse(null);
            
            if (operator == null) {
                response.put("success", false);
                response.put("message", "Không tìm thấy thông tin người dùng!");
                return ResponseEntity.status(403).body(response);
            }
            
            // Thực hiện lệnh Docker để lấy danh sách các container
            Process process = Runtime.getRuntime().exec("docker ps --format \"{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            int containersFound = 0;
            int containersUpdated = 0;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    String containerId = parts[0];
                    String containerName = parts[1];
                    
                    // Lấy IP của container
                    String containerIp = getContainerIpByName(containerName);
                    
                    Map<String, Object> containerInfo = new HashMap<>();
                    containerInfo.put("id", containerId);
                    containerInfo.put("name", containerName);
                    containerInfo.put("ip", containerIp);
                    if (parts.length >= 3) containerInfo.put("image", parts[2]);
                    if (parts.length >= 4) containerInfo.put("status", parts[3]);
                    
                    containerInfoList.add(containerInfo);
                    containersFound++;
                    
                    // Kiểm tra xem container có tồn tại trong DB của thiết bị không
                    List<Device> devices = deviceRepository.findByDeviceName(containerName);
                    if (!devices.isEmpty()) {
                        for (Device device : devices) {
                            // Cập nhật IP nếu cần
                            if (containerIp != null && !containerIp.isEmpty() && 
                                    (device.getIpAddress() == null || !device.getIpAddress().equals(containerIp))) {
                                device.setIpAddress(containerIp);
                                deviceRepository.save(device);
                                containersUpdated++;
                            }
                        }
                    } else {
                        // Tạo thiết bị mới nếu không tìm thấy
                        // Bạn có thể bật/tắt tính năng này
                        // Device newDevice = new Device();
                        // newDevice.setDeviceName(containerName);
                        // newDevice.setIpAddress(containerIp);
                        // newDevice.setDescription("Auto-detected Docker container");
                        // deviceRepository.save(newDevice);
                        // containersUpdated++;
                    }
                }
            }
            
            process.waitFor();
            reader.close();
            
            response.put("success", true);
            response.put("containersFound", containersFound);
            response.put("containersUpdated", containersUpdated);
            response.put("containers", containerInfoList);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi khi quét container: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
} 