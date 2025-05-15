package com.vn.demo.rest;

import com.vn.demo.dao.RoleRepository;
import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.Role;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * Hiển thị danh sách người dùng (cho admin)
     */
    @GetMapping("/list")
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "user-list";
    }
    
    /**
     * Hiển thị form cập nhật mật khẩu cho một user cụ thể (cho admin)
     */
    @GetMapping("/edit/{userId}")
    public String editUserForm(@PathVariable("userId") Integer userId, Model model, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(userId).orElse(null);
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy thông tin người dùng!");
            return "redirect:/user/list";
        }
        
        model.addAttribute("user", user);
        return "user-edit";
    }
    
    /**
     * Xử lý cập nhật mật khẩu cho một user cụ thể (cho admin)
     */
    @PostMapping("/admin-update-password")
    public String adminUpdatePassword(@RequestParam Integer userId,
                                     @RequestParam String newPassword,
                                     @RequestParam String confirmPassword,
                                     RedirectAttributes redirectAttributes) {
        
        // Tìm user trong database
        User user = userRepository.findById(userId).orElse(null);
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy thông tin người dùng!");
            return "redirect:/user/list";
        }
        
        // Kiểm tra xác nhận mật khẩu mới
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xác nhận mật khẩu mới không khớp!");
            return "redirect:/user/edit/" + userId;
        }
        
        // Cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        redirectAttributes.addFlashAttribute("successMessage", "Mật khẩu đã được cập nhật thành công!");
        return "redirect:/user/list";
    }
    
    /**
     * Xử lý cập nhật mật khẩu người dùng
     */
    @PostMapping("/updatePassword")
    public String updatePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        
        // Lấy thông tin người dùng hiện tại
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        
        // Tìm user trong database
        User user = userRepository.findByUser_name(currentUsername)
                .orElse(null);
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy thông tin người dùng!");
            return "redirect:/admin/dashboard";
        }
        
        // Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu hiện tại không đúng!");
            return "redirect:/admin/dashboard";
        }
        
        // Kiểm tra xác nhận mật khẩu mới
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xác nhận mật khẩu mới không khớp!");
            return "redirect:/admin/dashboard";
        }
        
        // Cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        redirectAttributes.addFlashAttribute("successMessage", "Mật khẩu đã được cập nhật thành công!");
        return "redirect:/admin/dashboard";
    }
    
    /**
     * Xử lý tạo người dùng mới
     */
    @PostMapping("/create")
    public String createUser(@RequestParam String username,
                             @RequestParam String email,
                             @RequestParam String password,
                             @RequestParam Integer roleId,
                             RedirectAttributes redirectAttributes) {
        
        // Kiểm tra username đã tồn tại chưa
        if (userRepository.existsByUser_name(username)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên đăng nhập đã tồn tại!");
            return "redirect:/admin/dashboard";
        }
        
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(email)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email đã tồn tại!");
            return "redirect:/admin/dashboard";
        }
        
        // Tìm role
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Quyền không hợp lệ!");
            return "redirect:/admin/dashboard";
        }
        
        // Tạo user mới
        User newUser = new User();
        newUser.setUser_name(username);
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.set_active(true);
        
        // Thiết lập quyền
        List<Role> roles = new ArrayList<>();
        roles.add(role);
        newUser.setRoles(roles);
        
        // Lưu user
        userRepository.save(newUser);
        
        redirectAttributes.addFlashAttribute("successMessage", "Tạo người dùng thành công!");
        return "redirect:/admin/dashboard";
    }
    
    /**
     * Xử lý cập nhật mật khẩu nhanh từ dashboard (cho admin)
     */
    @PostMapping("/quick-update-password")
    public String quickUpdatePassword(@RequestParam Integer userId,
                                    @RequestParam String newPassword,
                                    @RequestParam String confirmPassword,
                                    RedirectAttributes redirectAttributes) {
        
        // Tìm user trong database
        User user = userRepository.findById(userId).orElse(null);
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy thông tin người dùng!");
            return "redirect:/admin/dashboard";
        }
        
        // Kiểm tra xác nhận mật khẩu mới
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xác nhận mật khẩu mới không khớp!");
            return "redirect:/admin/dashboard";
        }
        
        // Cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        redirectAttributes.addFlashAttribute("successMessage", "Mật khẩu của người dùng " + user.getUser_name() + " đã được cập nhật thành công!");
        return "redirect:/admin/dashboard";
    }
} 