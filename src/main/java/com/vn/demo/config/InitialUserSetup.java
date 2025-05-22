package com.vn.demo.config;

import com.vn.demo.dao.RoleRepository;
import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.Role;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
public class InitialUserSetup implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Tạo tài khoản admin mặc định nếu chưa tồn tại
        if (!userRepository.existsByUser_name("admin")) {
            User adminUser = new User();
            adminUser.setUser_name("admin");
            // Đảm bảo mật khẩu được mã hóa bằng BCrypt
            String encodedPassword = passwordEncoder.encode("admin123");
            adminUser.setPassword(encodedPassword);
            adminUser.setEmail("admin@example.com");
            adminUser.set_active(true);
            
            Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN");
            
            List<Role> roles = new ArrayList<>();
            roles.add(adminRole);
            adminUser.setRoles(roles);
            
            userRepository.save(adminUser);
            
            System.out.println("Tài khoản admin mặc định đã được tạo với mật khẩu được mã hóa BCrypt");
        }
        
        // Tạo tài khoản team lead mặc định nếu chưa tồn tại
        if (!userRepository.existsByUser_name("teamlead")) {
            User teamLeadUser = new User();
            teamLeadUser.setUser_name("teamlead");
            // Đảm bảo mật khẩu được mã hóa bằng BCrypt
            String encodedPassword = passwordEncoder.encode("teamlead123");
            teamLeadUser.setPassword(encodedPassword);
            teamLeadUser.setEmail("teamlead@example.com");
            teamLeadUser.set_active(true);
            
            Role teamLeadRole = roleRepository.findByRoleName("ROLE_TEAMLEAD");
            
            List<Role> roles = new ArrayList<>();
            roles.add(teamLeadRole);
            teamLeadUser.setRoles(roles);
            
            userRepository.save(teamLeadUser);
            
            System.out.println("Tài khoản teamlead mặc định đã được tạo với mật khẩu được mã hóa BCrypt");
        }
        
        // Tạo tài khoản operator mặc định nếu chưa tồn tại
        if (!userRepository.existsByUser_name("operator")) {
            User operatorUser = new User();
            operatorUser.setUser_name("operator");
            // Đảm bảo mật khẩu được mã hóa bằng BCrypt
            String encodedPassword = passwordEncoder.encode("operator123");
            operatorUser.setPassword(encodedPassword);
            operatorUser.setEmail("operator@example.com");
            operatorUser.set_active(true);
            
            Role operatorRole = roleRepository.findByRoleName("ROLE_OPERATOR");
            
            List<Role> roles = new ArrayList<>();
            roles.add(operatorRole);
            operatorUser.setRoles(roles);
            
            userRepository.save(operatorUser);
            
            System.out.println("Tài khoản operator mặc định đã được tạo với mật khẩu được mã hóa BCrypt");
        }
        
        // Tạo tài khoản supervisor mặc định nếu chưa tồn tại
        if (!userRepository.existsByUser_name("supervisor")) {
            User supervisorUser = new User();
            supervisorUser.setUser_name("supervisor");
            // Đảm bảo mật khẩu được mã hóa bằng BCrypt
            String encodedPassword = passwordEncoder.encode("supervisor123");
            supervisorUser.setPassword(encodedPassword);
            supervisorUser.setEmail("supervisor@example.com");
            supervisorUser.set_active(true);
            
            Role supervisorRole = roleRepository.findByRoleName("ROLE_SUPERVISOR");
            
            List<Role> roles = new ArrayList<>();
            roles.add(supervisorRole);
            supervisorUser.setRoles(roles);
            
            userRepository.save(supervisorUser);
            
            System.out.println("Tài khoản supervisor mặc định đã được tạo với mật khẩu được mã hóa BCrypt");
        }
    }
} 