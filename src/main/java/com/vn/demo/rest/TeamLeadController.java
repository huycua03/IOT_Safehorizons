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
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/teamlead")
@PreAuthorize("hasAuthority('ROLE_TEAMLEAD')")
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
    
    @PersistenceContext
    private EntityManager entityManager;
    
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
    
    @GetMapping("/device/list")
    public String listDevices(Model model) {
        try {
            List<Device> devices = deviceRepository.findAll();
            model.addAttribute("devices", devices);
            return "device-list";
        } catch (Exception e) {
            // Log the exception
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading devices: " + e.getMessage());
            
            // Add an empty list to avoid template errors
            model.addAttribute("devices", new ArrayList<Device>());
            return "device-list";
        }
    }
    
    @GetMapping("/device/create")
    public String deviceCreateForm(Model model) {
        // Add any data needed for the device creation form
        return "device-create-form";
    }
    
    // 1. Quản lý thiết bị
    @PostMapping("/device/create")
    public String createDevice(@RequestParam String deviceName, 
                              @RequestParam(required = false) String ipAddress,
                              RedirectAttributes redirectAttributes) {
        // Lấy thông tin người dùng hiện tại (Team Lead)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Device device = new Device();
        device.setDeviceName(deviceName);
        device.setIpAddress(ipAddress != null ? ipAddress : ""); // Use provided IP address or empty string
        device.setStatus("Active"); // Trạng thái mặc định
        
        deviceRepository.save(device);
        
        redirectAttributes.addFlashAttribute("successMessage", "Thiết bị đã được tạo thành công!");
        return "redirect:/teamlead/device/list";
    }
    
    @GetMapping("/device/delete/{deviceId}")
    public String deleteDevice(@PathVariable("deviceId") Integer deviceId, RedirectAttributes redirectAttributes) {
        try {
            Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
            
            if (!deviceOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy thiết bị với ID: " + deviceId);
                return "redirect:/teamlead/device/list";
            }
            
            Device device = deviceOpt.get();
            
            // 1. Kiểm tra xem thiết bị có trong session nào không
            if (device.getSessions() != null && !device.getSessions().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Không thể xóa thiết bị vì nó đang được sử dụng trong " + device.getSessions().size() + 
                    " phiên làm việc. Vui lòng xóa các phiên làm việc trước.");
                return "redirect:/teamlead/device/list";
            }
            
            // 2. Kiểm tra xem thiết bị có trong nhóm nào không
            if (device.getDeviceGroup() != null) {
                // Thiết bị đang ở trong group, cần gỡ ra trước
                device.setDeviceGroup(null);
                deviceRepository.save(device);
            }
            
            // 3. Xóa các bản ghi lịch sử trạng thái của thiết bị
            if (device.getDeviceStatusHistories() != null && !device.getDeviceStatusHistories().isEmpty()) {
                device.getDeviceStatusHistories().clear();
                deviceRepository.save(device);
            }
            
            // 4. Bây giờ có thể xóa thiết bị
            deviceRepository.delete(device);
            redirectAttributes.addFlashAttribute("successMessage", "Thiết bị đã được xóa thành công!");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi xóa thiết bị: " + e.getMessage());
        }
        
        return "redirect:/teamlead/device/list";
    }
    
    // 2. Quản lý nhóm thiết bị
    @GetMapping("/devicegroup/list")
    public String listDeviceGroups(Model model) {
        try {
            List<DeviceGroup> deviceGroups = deviceGroupRepository.findAll();
            model.addAttribute("deviceGroups", deviceGroups);
            return "device-group-list";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading device groups: " + e.getMessage());
            model.addAttribute("deviceGroups", new ArrayList<DeviceGroup>());
            return "device-group-list";
        }
    }
    
    @GetMapping("/devicegroup/create")
    public String deviceGroupCreateForm(Model model) {
        // Get list of available devices
        List<Device> devices = deviceRepository.findAll();
        model.addAttribute("devices", devices);
        model.addAttribute("group", new DeviceGroup());
        return "device-group-form";
    }
    
    @PostMapping("/devicegroup/create")
    public String createDeviceGroup(@RequestParam String groupName,
                                   @RequestParam(required = false) List<Integer> deviceIds,
                                   RedirectAttributes redirectAttributes) {
        try {
            // Get current logged in team lead
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User teamLead = userRepository.findByUser_name(username).orElse(null);
            
            DeviceGroup deviceGroup = new DeviceGroup();
            deviceGroup.setGroupName(groupName);
            deviceGroup.setTeamLead(teamLead);
            
            // Save the group first to get an ID
            deviceGroup = deviceGroupRepository.save(deviceGroup);
            
            // Add selected devices to the group
            if (deviceIds != null && !deviceIds.isEmpty()) {
                List<Device> devices = deviceRepository.findAllById(deviceIds);
                for (Device device : devices) {
                    device.setDeviceGroup(deviceGroup);
                    deviceRepository.save(device);
                }
                
                // Refresh the device group
                deviceGroup = deviceGroupRepository.findById(deviceGroup.getGroupId()).orElse(deviceGroup);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "Device group has been created successfully!");
            return "redirect:/teamlead/devicegroup/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating device group: " + e.getMessage());
            return "redirect:/teamlead/devicegroup/create";
        }
    }
    
    @GetMapping("/devicegroup/view/{groupId}")
    public String viewDeviceGroup(@PathVariable("groupId") Integer groupId, Model model, RedirectAttributes redirectAttributes) {
        try {
            Optional<DeviceGroup> groupOpt = deviceGroupRepository.findById(groupId);
            
            if (groupOpt.isPresent()) {
                DeviceGroup group = groupOpt.get();
                model.addAttribute("group", group);
                return "device-group-view";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Device group not found!");
                return "redirect:/teamlead/devicegroup/list";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error viewing device group: " + e.getMessage());
            return "redirect:/teamlead/devicegroup/list";
        }
    }
    
    @GetMapping("/devicegroup/edit/{groupId}")
    public String editDeviceGroupForm(@PathVariable("groupId") Integer groupId, Model model, RedirectAttributes redirectAttributes) {
        try {
            Optional<DeviceGroup> groupOpt = deviceGroupRepository.findById(groupId);
            
            if (groupOpt.isPresent()) {
                DeviceGroup group = groupOpt.get();
                List<Device> allDevices = deviceRepository.findAll();
                
                model.addAttribute("group", group);
                model.addAttribute("devices", allDevices);
                return "device-group-form";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Device group not found!");
                return "redirect:/teamlead/devicegroup/list";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading device group: " + e.getMessage());
            return "redirect:/teamlead/devicegroup/list";
        }
    }
    
    @PostMapping("/devicegroup/update/{groupId}")
    public String updateDeviceGroup(@PathVariable("groupId") Integer groupId,
                                  @RequestParam String groupName,
                                  @RequestParam(required = false) List<Integer> deviceIds,
                                  RedirectAttributes redirectAttributes) {
        try {
            Optional<DeviceGroup> groupOpt = deviceGroupRepository.findById(groupId);
            
            if (!groupOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Device group not found!");
                return "redirect:/teamlead/devicegroup/list";
            }
            
            DeviceGroup deviceGroup = groupOpt.get();
            deviceGroup.setGroupName(groupName);
            
            // Remove all existing device associations
            List<Device> currentDevices = deviceGroup.getDevices();
            if (currentDevices != null) {
                for (Device device : currentDevices) {
                    device.setDeviceGroup(null);
                    deviceRepository.save(device);
                }
            }
            
            // Save the group
            deviceGroupRepository.save(deviceGroup);
            
            // Add selected devices to the group
            if (deviceIds != null && !deviceIds.isEmpty()) {
                List<Device> devices = deviceRepository.findAllById(deviceIds);
                for (Device device : devices) {
                    device.setDeviceGroup(deviceGroup);
                    deviceRepository.save(device);
                }
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "Device group has been updated successfully!");
            return "redirect:/teamlead/devicegroup/view/" + groupId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating device group: " + e.getMessage());
            return "redirect:/teamlead/devicegroup/edit/" + groupId;
        }
    }
    
    @GetMapping("/devicegroup/delete/{groupId}")
    public String deleteDeviceGroup(@PathVariable("groupId") Integer groupId, RedirectAttributes redirectAttributes) {
        try {
            Optional<DeviceGroup> groupOpt = deviceGroupRepository.findById(groupId);
            
            if (!groupOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Device group not found!");
                return "redirect:/teamlead/devicegroup/list";
            }
            
            DeviceGroup deviceGroup = groupOpt.get();
            
            // Remove device associations first
            List<Device> devices = deviceGroup.getDevices();
            if (devices != null) {
                for (Device device : devices) {
                    device.setDeviceGroup(null);
                    deviceRepository.save(device);
                }
            }
            
            // Remove profile associations
            List<Profile> profiles = deviceGroup.getProfiles();
            if (profiles != null && !profiles.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Cannot delete device group - it is used by " + profiles.size() + " profiles. Please remove these associations first.");
                return "redirect:/teamlead/devicegroup/view/" + groupId;
            }
            
            // Delete the group
            deviceGroupRepository.delete(deviceGroup);
            
            redirectAttributes.addFlashAttribute("successMessage", "Device group has been deleted successfully!");
            return "redirect:/teamlead/devicegroup/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting device group: " + e.getMessage());
            return "redirect:/teamlead/devicegroup/list";
        }
    }
    
    // 3. Quản lý danh sách lệnh
    @GetMapping("/commandlist/list")
    public String listCommandLists(Model model) {
        try {
            List<CommandList> commandLists = commandListRepository.findAll();
            model.addAttribute("commandLists", commandLists);
            return "command-list";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading command lists: " + e.getMessage());
            model.addAttribute("commandLists", new ArrayList<CommandList>());
            return "command-list";
        }
    }
    
    @GetMapping("/command/list")
    public String listCommands(Model model) {
        try {
            List<Command> commands = new ArrayList<>();
            List<CommandList> commandLists = commandListRepository.findAll();
            for (CommandList list : commandLists) {
                commands.addAll(list.getCommands());
            }
            model.addAttribute("commands", commands);
            model.addAttribute("commandLists", commandLists);
            return "command-list-all";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading commands: " + e.getMessage());
            model.addAttribute("commands", new ArrayList<Command>());
            return "command-list-all";
        }
    }
    
    @GetMapping("/command/create")
    public String commandCreateForm(Model model) {
        List<CommandList> commandLists = commandListRepository.findAll();
        model.addAttribute("commandLists", commandLists);
        model.addAttribute("command", new Command());
        return "command-form";
    }
    
    @PostMapping("/command/create")
    public String createCommand(@RequestParam String commandText,
                             @RequestParam Integer commandListId,
                             RedirectAttributes redirectAttributes) {
        try {
            CommandList commandList = commandListRepository.findById(commandListId).orElse(null);
            
            if (commandList == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Command List không tồn tại!");
                return "redirect:/teamlead/command/create";
            }
            
            Command command = new Command();
            command.setCommandText(commandText);
            command.setCommandList(commandList);
            
            // Thêm command vào danh sách commands của commandList
            List<Command> commands = commandList.getCommands();
            if (commands == null) {
                commands = new ArrayList<>();
            }
            commands.add(command);
            commandList.setCommands(commands);
            
            commandListRepository.save(commandList);
            
            redirectAttributes.addFlashAttribute("successMessage", "Command đã được tạo thành công!");
            return "redirect:/teamlead/command/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi tạo command: " + e.getMessage());
            return "redirect:/teamlead/command/create";
        }
    }
    
    @GetMapping("/command/delete/{commandId}")
    public String deleteCommand(@PathVariable("commandId") Integer commandId, RedirectAttributes redirectAttributes) {
        try {
            // Trong trường hợp thực tế, cần thêm logic để kiểm tra xem command có đang được sử dụng không
            commandListRepository.deleteCommandById(commandId);
            redirectAttributes.addFlashAttribute("successMessage", "Command đã được xóa thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi xóa command: " + e.getMessage());
        }
        return "redirect:/teamlead/command/list";
    }
    
    @GetMapping("/commandlist/create")
    public String commandListCreateForm(Model model) {
        model.addAttribute("commandList", new CommandList());
        return "command-list-form";
    }
    
    @PostMapping("/commandlist/create")
    public String createCommandList(@RequestParam String listName,
                                  RedirectAttributes redirectAttributes) {
        // Lấy thông tin người dùng hiện tại (Team Lead)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User teamLead = userRepository.findByUser_name(username).orElse(null);
        
        CommandList commandList = new CommandList();
        commandList.setName(listName);
        commandList.setTeamLead(teamLead);
        commandList.setCommands(new ArrayList<>());
        
        commandListRepository.save(commandList);
        
        redirectAttributes.addFlashAttribute("successMessage", "Danh sách lệnh đã được tạo thành công!");
        return "redirect:/teamlead/commandlist/list";
    }
    
    @GetMapping("/commandlist/addcommand/{commandListId}")
    public String addCommandToListForm(@PathVariable("commandListId") Integer commandListId, Model model) {
        CommandList commandList = commandListRepository.findById(commandListId).orElse(null);
        
        if (commandList == null) {
            model.addAttribute("errorMessage", "Command List không tồn tại!");
            return "redirect:/teamlead/commandlist/list";
        }
        
        model.addAttribute("commandList", commandList);
        model.addAttribute("command", new Command());
        return "command-add-to-list";
    }
    
    @PostMapping("/commandlist/addcommand")
    public String addCommandToList(@RequestParam Integer commandListId,
                                @RequestParam String commandText,
                                @RequestParam(required = false) String multipleCommands,
                                RedirectAttributes redirectAttributes) {
        try {
            CommandList commandList = commandListRepository.findById(commandListId).orElse(null);
            
            if (commandList == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Command List không tồn tại!");
                return "redirect:/teamlead/commandlist/list";
            }
            
            List<Command> commands = commandList.getCommands();
            if (commands == null) {
                commands = new ArrayList<>();
            }
            
            // Kiểm tra nếu có nhiều lệnh được nhập
            if (multipleCommands != null && !multipleCommands.trim().isEmpty()) {
                String[] commandLines = multipleCommands.split("\\r?\\n");
                
                for (String cmdText : commandLines) {
                    if (!cmdText.trim().isEmpty()) {
                        Command command = new Command();
                        command.setCommandText(cmdText.trim());
                        command.setCommandList(commandList);
                        commands.add(command);
                    }
                }
            } else if (commandText != null && !commandText.trim().isEmpty()) {
                // Thêm lệnh đơn lẻ
                Command command = new Command();
                command.setCommandText(commandText.trim());
                command.setCommandList(commandList);
                commands.add(command);
            }
            
            commandList.setCommands(commands);
            commandListRepository.save(commandList);
            
            redirectAttributes.addFlashAttribute("successMessage", "Command đã được thêm vào danh sách thành công!");
            return "redirect:/teamlead/commandlist/view/" + commandListId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi thêm command: " + e.getMessage());
            return "redirect:/teamlead/commandlist/addcommand/" + commandListId;
        }
    }
    
    @GetMapping("/commandlist/view/{commandListId}")
    public String viewCommandList(@PathVariable("commandListId") Integer commandListId, Model model) {
        try {
            CommandList commandList = commandListRepository.findById(commandListId).orElse(null);
            
            if (commandList == null) {
                model.addAttribute("errorMessage", "Command List không tồn tại!");
                return "redirect:/teamlead/commandlist/list";
            }
            
            model.addAttribute("commandList", commandList);
            model.addAttribute("commands", commandList.getCommands());
            return "command-list-detail";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Lỗi khi xem command list: " + e.getMessage());
            return "redirect:/teamlead/commandlist/list";
        }
    }
    
    @GetMapping("/commandlist/delete/{commandListId}")
    @Transactional
    public String deleteCommandList(@PathVariable("commandListId") Integer commandListId, RedirectAttributes redirectAttributes) {
        try {
            // Kiểm tra xem command list có đang được sử dụng bởi profile nào không
            int profilesCount = commandListRepository.countProfilesUsingCommandList(commandListId);
            
            if (profilesCount > 0) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Không thể xóa! Danh sách lệnh này đang được sử dụng bởi " + profilesCount + " profile.");
                return "redirect:/teamlead/commandlist/list";
            }
            
            // Lấy thông tin về CommandList trước khi xóa
            CommandList commandList = commandListRepository.findById(commandListId).orElse(null);
            if (commandList == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy danh sách lệnh!");
                return "redirect:/teamlead/commandlist/list";
            }
            
            // Xóa tất cả command trong command list
            commandListRepository.deleteAllCommandsInList(commandListId);
            
            // Xử lý mối quan hệ với TeamLead để tránh lỗi khóa ngoại
            commandList.setTeamLead(null);
            commandListRepository.save(commandList);
            
            // Xóa command list
            commandListRepository.deleteById(commandListId);
            
            redirectAttributes.addFlashAttribute("successMessage", "Danh sách lệnh đã được xóa thành công!");
        } catch (Exception e) {
            e.printStackTrace(); // Log chi tiết lỗi
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi xóa danh sách lệnh: " + e.getMessage());
        }
        
        return "redirect:/teamlead/commandlist/list";
    }
    
    // 4. Quản lý profile
    @GetMapping("/profile/list")
    public String listProfiles(Model model) {
        try {
            List<Profile> profiles = profileRepository.findAll();
            model.addAttribute("profiles", profiles);
            return "profile-list";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading profiles: " + e.getMessage());
            model.addAttribute("profiles", new ArrayList<Profile>());
            return "profile-list";
        }
    }
    
    @GetMapping("/profile/create")
    public String profileCreateForm(Model model) {
        // Get device groups and command lists for dropdown
        List<DeviceGroup> deviceGroups = deviceGroupRepository.findAll();
        List<CommandList> commandLists = commandListRepository.findAll();
        
        model.addAttribute("deviceGroups", deviceGroups);
        model.addAttribute("commandLists", commandLists);
        model.addAttribute("profile", new Profile());
        return "profile-form";
    }
    
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
    
    // Thêm phương thức xem chi tiết profile
    @GetMapping("/profile/view/{profileId}")
    public String viewProfile(@PathVariable("profileId") Integer profileId, Model model) {
        try {
            Profile profile = profileRepository.findById(profileId).orElse(null);
            
            if (profile == null) {
                model.addAttribute("errorMessage", "Không tìm thấy profile!");
                return "redirect:/teamlead/profile/list";
            }
            
            // Lấy danh sách người dùng được gán profile này
            List<OperatorProfile> operatorProfiles = operatorProfileRepository.findByProfileId(profileId);
            
            model.addAttribute("profile", profile);
            model.addAttribute("operatorProfiles", operatorProfiles);
            return "profile-view";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Lỗi khi xem profile: " + e.getMessage());
            return "redirect:/teamlead/profile/list";
        }
    }
    
    // Thêm phương thức GET để hiển thị form gán profile
    @GetMapping("/profile/assign")
    public String assignProfileForm(@RequestParam Integer profileId, Model model) {
        Profile profile = profileRepository.findById(profileId).orElse(null);
        
        if (profile == null) {
            model.addAttribute("errorMessage", "Không tìm thấy profile!");
            return "redirect:/teamlead/profile/list";
        }
        
        // Lấy danh sách người dùng operator
        List<User> operators = userRepository.findOperators();
        
        model.addAttribute("profile", profile);
        model.addAttribute("operators", operators);
        return "profile-assign-form";
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
    
    // Gán profile cho nhiều người dùng cùng lúc
    @PostMapping("/profile/assign-multiple")
    public String assignProfileToMultipleUsers(@RequestParam Integer profileId,
                                             @RequestParam(required = false) List<Integer> userIds,
                                             RedirectAttributes redirectAttributes) {
        // Kiểm tra xem có profile và danh sách người dùng không
        Profile profile = profileRepository.findById(profileId).orElse(null);
        
        if (profile == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy profile!");
            return "redirect:/teamlead/profile/list";
        }
        
        // Kiểm tra xem đã chọn người dùng nào chưa
        if (userIds == null || userIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn ít nhất một người dùng để gán profile!");
            return "redirect:/teamlead/profile/assign?profileId=" + profileId;
        }
        
        try {
            // Lấy thông tin người dùng hiện tại (Team Lead) làm người gán
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User teamLead = userRepository.findByUser_name(username).orElse(null);
            
            int successCount = 0;
            int existingCount = 0;
            
            for (Integer userId : userIds) {
                User user = userRepository.findById(userId).orElse(null);
                
                if (user == null) {
                    continue; // Bỏ qua người dùng không tồn tại
                }
                
                // Kiểm tra xem profile đã được gán cho người dùng này chưa
                boolean alreadyAssigned = false;
                if (user.getOperatorProfiles() != null) {
                    for (OperatorProfile op : user.getOperatorProfiles()) {
                        if (op.getProfile().getProfileId() == profileId) {
                            alreadyAssigned = true;
                            break;
                        }
                    }
                }
                
                if (alreadyAssigned) {
                    existingCount++;
                    continue; // Bỏ qua nếu đã được gán
                }
                
                // Tạo mới OperatorProfile
                OperatorProfile operatorProfile = new OperatorProfile();
                OperatorProfileId id = new OperatorProfileId(profileId, userId);
                operatorProfile.setId(id);
                operatorProfile.setOperator(user);
                operatorProfile.setProfile(profile);
                operatorProfile.setAssignedBy(teamLead);
                operatorProfile.setAssignedAt(LocalDateTime.now());
                
                // Lưu OperatorProfile
                operatorProfileRepository.save(operatorProfile);
                successCount++;
            }
            
            // Tạo thông báo phù hợp dựa trên kết quả
            if (successCount > 0) {
                String message = "Đã gán profile cho " + successCount + " người dùng thành công!";
                if (existingCount > 0) {
                    message += " (Có " + existingCount + " người dùng đã được gán profile này trước đó)";
                }
                redirectAttributes.addFlashAttribute("successMessage", message);
            } else if (existingCount > 0) {
                redirectAttributes.addFlashAttribute("warningMessage", "Tất cả " + existingCount + " người dùng đã được gán profile này trước đó!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể gán profile cho bất kỳ người dùng nào!");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi gán profile: " + e.getMessage());
        }
        
        return "redirect:/teamlead/profile/view/" + profileId;
    }
    
    // Thêm phương thức mới để xóa profile
    @GetMapping("/profile/delete/{profileId}")
    @Transactional
    public String deleteProfile(@PathVariable("profileId") Integer profileId, RedirectAttributes redirectAttributes) {
        try {
            Optional<Profile> profileOpt = profileRepository.findById(profileId);
            
            if (!profileOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy profile!");
                return "redirect:/teamlead/profile/list";
            }
            
            Profile profile = profileOpt.get();
            
            // 1. Xóa tất cả các operatorProfile liên quan bằng câu lệnh native SQL 
            entityManager.createNativeQuery("DELETE FROM operator_profile WHERE profile_id = :profileId")
                .setParameter("profileId", profileId)
                .executeUpdate();
                
            // 2. Xóa tất cả các assignment liên quan bằng câu lệnh native SQL nếu có
            entityManager.createNativeQuery("DELETE FROM assignment WHERE profile_id = :profileId")
                .setParameter("profileId", profileId)
                .executeUpdate();
            
            // 3. Xóa profile
            entityManager.createNativeQuery("DELETE FROM profile WHERE profile_id = :profileId")
                .setParameter("profileId", profileId)
                .executeUpdate();
            
            // 4. Kiểm tra xem profile đã thực sự bị xóa chưa
            boolean stillExists = profileRepository.existsById(profileId);
            if (stillExists) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa profile hoàn toàn. Vui lòng liên hệ admin.");
                return "redirect:/teamlead/profile/list";
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "Profile đã được xóa thành công!");
            
        } catch (Exception e) {
            e.printStackTrace(); // Log lỗi để debug
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi xóa profile: " + e.getMessage());
        }
        
        return "redirect:/teamlead/profile/list";
    }
} 