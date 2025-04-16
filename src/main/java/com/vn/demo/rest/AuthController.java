package com.vn.demo.rest;

import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/api/auth/login")
    public String authenticateUser(@RequestParam String username, 
                                  @RequestParam String password,
                                  HttpServletRequest request,
                                  Model model) {
        try {
            // Log để kiểm tra thông tin
            System.out.println("Đang thử đăng nhập với username: " + username);
            
            // Kiểm tra xem user có tồn tại không
            Optional<User> userOpt = userRepository.findByUser_name(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                System.out.println("Tìm thấy user: " + user.getUser_name());
                System.out.println("Mật khẩu đã lưu: " + user.getPassword());
                System.out.println("Mật khẩu matches: " + passwordEncoder.matches(password, user.getPassword()));
            } else {
                System.out.println("Không tìm thấy user với username: " + username);
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Update last login time
            userRepository.findByUser_name(username).ifPresent(user -> {
                user.setLast_login(new Date());
                userRepository.save(user);
            });
            
            System.out.println("Đăng nhập thành công, đang chuyển hướng đến /dashboard");
            return "redirect:/dashboard";
        } catch (Exception e) {
            System.out.println("Lỗi đăng nhập: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không chính xác");
            return "login";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        System.out.println("Đã vào dashboard controller");
        return "dashboard";
    }
} 