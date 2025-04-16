package com.vn.demo.config;

import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class PasswordResetConfig implements ApplicationRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        // Lấy tất cả người dùng từ cơ sở dữ liệu
        List<User> users = userRepository.findAll();
        
        for (User user : users) {
            String storedPassword = user.getPassword();
            // Kiểm tra nếu mật khẩu không phải định dạng BCrypt (không bắt đầu với $2a$)
            if (storedPassword == null || !storedPassword.startsWith("$2a$")) {
                System.out.println("Mật khẩu không đúng định dạng BCrypt cho user: " + user.getUser_name());
                
                // Mặc định đặt lại mật khẩu thành "password123" nếu là admin, hoặc kết hợp username + "123"
                String defaultPassword = "admin".equals(user.getUser_name()) ? "admin123" : user.getUser_name() + "123";
                String encodedPassword = passwordEncoder.encode(defaultPassword);
                user.setPassword(encodedPassword);
                
                userRepository.save(user);
                System.out.println("Đã đặt lại mật khẩu cho user: " + user.getUser_name());
            }
        }
    }
} 