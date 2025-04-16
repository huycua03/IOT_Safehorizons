package com.vn.demo.config;

import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.Role;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class InitialUserSetup implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

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
            
            Role adminRole = new Role();
            adminRole.setRoleName("ROLE_ADMIN");
            
            List<Role> roles = new ArrayList<>();
            roles.add(adminRole);
            adminUser.setRoles(roles);
            
            userRepository.save(adminUser);
            
            System.out.println("Tài khoản admin mặc định đã được tạo với mật khẩu được mã hóa BCrypt");
        }
    }
} 