package com.vn.demo.config;

import com.vn.demo.dao.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserRepository userRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                                Authentication authentication) throws IOException, ServletException {
                // Update last login time
                userRepository.findByUser_name(authentication.getName()).ifPresent(user -> {
                    user.setLast_login(new Date());
                    userRepository.save(user);
                    System.out.println("Updated last login time for user: " + user.getUser_name());
                });
                
                // Log the authentication details
                System.out.println("Authentication success for user: " + authentication.getName());
                System.out.println("Authorities: " + authentication.getAuthorities());
                
                // Determine where to redirect based on role
                String targetUrl = determineTargetUrl(authentication);
                System.out.println("Redirecting to: " + targetUrl);
                
                // Redirect to the appropriate URL
                getRedirectStrategy().sendRedirect(request, response, targetUrl);
            }
            
            protected String determineTargetUrl(Authentication authentication) {
                for (GrantedAuthority authority : authentication.getAuthorities()) {
                    String role = authority.getAuthority();
                    System.out.println("Checking role: " + role);
                    
                    if (role.equals("ROLE_ADMIN")) {
                        return "/admin/dashboard";
                    }
                    
                    if (role.equals("ROLE_TEAMLEAD")) {
                        return "/teamlead/dashboard";
                    }
                    
                    if (role.equals("ROLE_OPERATOR")) {
                        return "/operator/dashboard";
                    }
                    
                    if (role.equals("ROLE_SUPERVISOR")) {
                        return "/supervisor/dashboard";
                    }
                }
                
                // Default to the dashboard
                return "/dashboard";
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/login", "/css/**", "/js/**", "/reset-password").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/api/auth/login")
                .successHandler(customAuthenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );

        // Thêm debug
        System.out.println("Đã cấu hình Security Filter Chain");
        
        return http.build();
    }
} 