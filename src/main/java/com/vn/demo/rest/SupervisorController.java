package com.vn.demo.rest;

import com.vn.demo.dao.UserRepository;
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
@RequestMapping("/supervisor")
@PreAuthorize("hasAuthority('SUPERVISOR')")
public class SupervisorController {

    @Autowired
    private UserRepository userRepository;
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Get current logged in supervisor
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> supervisorOpt = userRepository.findByUser_name(username);
        
        if (supervisorOpt.isPresent()) {
            User supervisor = supervisorOpt.get();
            model.addAttribute("supervisor", supervisor);
            
            // Add more data for supervisor dashboard as needed
        }
        
        return "supervisor-dashboard";
    }
} 