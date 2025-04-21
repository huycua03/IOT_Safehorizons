package com.vn.demo.rest;

import com.vn.demo.dao.UserRepository;
import com.vn.demo.dao.ProfileRepository;
import com.vn.demo.entity.User;
import com.vn.demo.entity.OperatorProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Controller
@RequestMapping("/operator")
@PreAuthorize("hasAuthority('OPERATOR')")
public class OperatorController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProfileRepository profileRepository;
    
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
} 