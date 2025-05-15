package com.vn.demo.rest;

import com.vn.demo.dao.RoleRepository;
import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.Role;
import com.vn.demo.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Controller
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        // Debug: Print all roles in the database
        List<Role> allRoles = roleRepository.findAll();
        System.out.println("All roles in the database:");
        for (Role role : allRoles) {
            System.out.println("Role ID: " + role.getRoleId() + ", Role Name: " + role.getRoleName());
        }
        
        return "login";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password";
    }
    
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("username") String username, Model model) {
        // Check if the username exists
        Optional<User> userOptional = userRepository.findByUser_name(username);
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Determine default password based on role
            String defaultPassword;
            if ("admin".equals(username)) {
                defaultPassword = "admin123";
            } else {
                defaultPassword = username + "123";
            }
            
            // Encode and save the default password
            user.setPassword(passwordEncoder.encode(defaultPassword));
            userRepository.save(user);
            
            model.addAttribute("Reset password thành công");
            System.out.println("Đã đặt lại mật khẩu cho user: " + user.getUser_name());
        } else {
            model.addAttribute("errorMessage", "Tên đăng nhập không tồn tại trong hệ thống");
        }
        
        return "reset-password";
    }
} 