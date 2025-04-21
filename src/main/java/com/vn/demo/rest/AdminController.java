package com.vn.demo.rest;

import com.vn.demo.dao.UserRepository;
import com.vn.demo.dao.RoleRepository;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Get current authenticated admin
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> adminOpt = userRepository.findByUser_name(username);
        
        if (adminOpt.isPresent()) {
            User admin = adminOpt.get();
            model.addAttribute("admin", admin);
        }
        
        // Add user and role data for admin dashboard
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("roles", roleRepository.findAll());
        
        return "admin-dashboard";
    }
} 