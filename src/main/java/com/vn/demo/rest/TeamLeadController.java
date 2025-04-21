package com.vn.demo.rest;

import com.vn.demo.dao.DeviceGroupRepository;
import com.vn.demo.dao.DeviceRepository;
import com.vn.demo.dao.CommandListRepository;
import com.vn.demo.dao.ProfileRepository;
import com.vn.demo.dao.UserRepository;
import com.vn.demo.dao.OperatorProfileRepository;
import com.vn.demo.entity.DeviceGroup;
import com.vn.demo.entity.Device;
import com.vn.demo.entity.CommandList;
import com.vn.demo.entity.Command;
import com.vn.demo.entity.Profile;
import com.vn.demo.entity.User;
import com.vn.demo.entity.OperatorProfile;
import com.vn.demo.entity.OperatorProfileId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/teamlead")
@PreAuthorize("hasAuthority('TEAM_LEAD')")
public class TeamLeadController {

    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private DeviceGroupRepository deviceGroupRepository;
    
    @Autowired
    private CommandListRepository commandListRepository;
    
    @Autowired
    private ProfileRepository profileRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private OperatorProfileRepository operatorProfileRepository;
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Lấy dữ liệu cần thiết cho trang dashboard
        model.addAttribute("devices", deviceRepository.findAll());
        model.addAttribute("deviceGroups", deviceGroupRepository.findAll());
        model.addAttribute("commandLists", commandListRepository.findAll());
        model.addAttribute("profiles", profileRepository.findAll());
        
        // Lấy danh sách người dùng operator
        List<User> operators = userRepository.findOperators();
        model.addAttribute("operators", operators);
        
        return "teamlead-dashboard";
    }
    
    // 1. Quản lý thiết bị
    @PostMapping("/device/create")
    public String createDevice(@RequestParam String deviceName, 
                              @RequestParam String deviceType,
                              RedirectAttributes redirectAttributes) {
        // Lấy thông tin người dùng hiện tại (Team Lead)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Device device = new Device();
        device.setDeviceName(deviceName);
        device.setIpAddress(""); // Để trống hoặc đặt giá trị mặc định
        device.setStatus("Active"); // Trạng thái mặc định
        
        deviceRepository.save(device);
        
        redirectAttributes.addFlashAttribute("successMessage", "Thiết bị đã được tạo thành công!");
        return "redirect:/teamlead/dashboard";
    }
    
    // 2. Quản lý nhóm thiết bị
    @PostMapping("/devicegroup/create")
    public String createDeviceGroup(@RequestParam String groupName,
                                   @RequestParam(required = false) List<Integer> deviceIds,
                                   RedirectAttributes redirectAttributes) {
        // Lấy thông tin người dùng hiện tại (Team Lead)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User teamLead = userRepository.findByUser_name(username).orElse(null);
        
        DeviceGroup deviceGroup = new DeviceGroup();
        deviceGroup.setGroupName(groupName);
        deviceGroup.setTeamLead(teamLead);
        
        if (deviceIds != null && !deviceIds.isEmpty()) {
            List<Device> devices = deviceRepository.findAllById(deviceIds);
            deviceGroup.setDevices(devices);
        }
        
        deviceGroupRepository.save(deviceGroup);
        
        redirectAttributes.addFlashAttribute("successMessage", "Nhóm thiết bị đã được tạo thành công!");
        return "redirect:/teamlead/dashboard";
    }
    
    // 3. Quản lý danh sách lệnh
    @PostMapping("/commandlist/create")
    public String createCommandList(@RequestParam String listName,
                                  @RequestParam String commands,
                                  RedirectAttributes redirectAttributes) {
        // Lấy thông tin người dùng hiện tại (Team Lead)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User teamLead = userRepository.findByUser_name(username).orElse(null);
        
        CommandList commandList = new CommandList();
        commandList.setName(listName);
        commandList.setTeamLead(teamLead);
        
        // Lưu danh sách lệnh trước để có ID
        commandList = commandListRepository.save(commandList);
        
        // Tạo các đối tượng Command từ chuỗi văn bản
        if (commands != null && !commands.isEmpty()) {
            List<Command> commandObjects = new ArrayList<>();
            String[] commandLines = commands.split("\\r?\\n");
            
            for (String commandText : commandLines) {
                if (!commandText.trim().isEmpty()) {
                    Command command = new Command();
                    command.setCommandText(commandText.trim());
                    command.setCommandList(commandList);
                    commandObjects.add(command);
                }
            }
            
            commandList.setCommands(commandObjects);
            commandListRepository.save(commandList);
        }
        
        redirectAttributes.addFlashAttribute("successMessage", "Danh sách lệnh đã được tạo thành công!");
        return "redirect:/teamlead/dashboard";
    }
    
    // 4. Quản lý profile
    @PostMapping("/profile/create")
    public String createProfile(@RequestParam String profileName,
                              @RequestParam Integer deviceGroupId,
                              @RequestParam Integer commandListId,
                              RedirectAttributes redirectAttributes) {
        DeviceGroup deviceGroup = deviceGroupRepository.findById(deviceGroupId).orElse(null);
        CommandList commandList = commandListRepository.findById(commandListId).orElse(null);
        
        if (deviceGroup == null || commandList == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy nhóm thiết bị hoặc danh sách lệnh!");
            return "redirect:/teamlead/dashboard";
        }
        
        Profile profile = new Profile();
        profile.setName(profileName);
        profile.setDeviceGroup(deviceGroup);
        profile.setCommandList(commandList);
        
        profileRepository.save(profile);
        
        redirectAttributes.addFlashAttribute("successMessage", "Profile đã được tạo thành công!");
        return "redirect:/teamlead/dashboard";
    }
    
    // 5. Gán profile cho người dùng operator
    @PostMapping("/profile/assign")
    public String assignProfile(@RequestParam Integer userId,
                              @RequestParam Integer profileId,
                              RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(userId).orElse(null);
        Profile profile = profileRepository.findById(profileId).orElse(null);
        
        if (user == null || profile == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy người dùng hoặc profile!");
            return "redirect:/teamlead/dashboard";
        }
        
        try {
            // Check if the profile is already assigned to this user
            if (user.getOperatorProfiles() != null) {
                for (OperatorProfile op : user.getOperatorProfiles()) {
                    if (op.getProfile().getProfileId() == profileId) {
                        throw new RuntimeException("User already has this profile assigned");
                    }
                }
            }
            
            // Get current user (Team Lead) as assignor
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User teamLead = userRepository.findByUser_name(username).orElse(null);
            
            // Create new OperatorProfile
            OperatorProfile operatorProfile = new OperatorProfile();
            OperatorProfileId id = new OperatorProfileId(profileId, userId);
            operatorProfile.setId(id);
            operatorProfile.setOperator(user);
            operatorProfile.setProfile(profile);
            operatorProfile.setAssignedBy(teamLead);
            operatorProfile.setAssignedAt(LocalDateTime.now());
            
            // Save the operator profile
            operatorProfileRepository.save(operatorProfile);
            
            redirectAttributes.addFlashAttribute("successMessage", "Đã gán profile cho người dùng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi gán profile: " + e.getMessage());
        }
        
        return "redirect:/teamlead/dashboard";
    }

    @GetMapping("/teamlead")
    public String teamlead(Model model) {
        // Get current logged in team lead
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> teamLeadOpt = userRepository.findByUser_name(username);
        
        if (teamLeadOpt.isPresent()) {
            User teamLead = teamLeadOpt.get();
            model.addAttribute("teamLead", teamLead);
        }
        
        return "teamlead-teamlead";
    }
} 