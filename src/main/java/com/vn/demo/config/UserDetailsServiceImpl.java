package com.vn.demo.config;

import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.Role;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUser_name(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + username));

        if (!user.is_active()) {
            throw new UsernameNotFoundException("Tài khoản đã bị khóa: " + username);
        }

        // Log role information for debugging
        System.out.println("Found user: " + username);
        System.out.println("User roles: " + user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.joining(", ")));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(Role::getRoleName)
                .map(roleName -> {
                    System.out.println("Adding authority: " + roleName);
                    return new SimpleGrantedAuthority(roleName);
                })
                .collect(Collectors.toList());

        return new org.springframework.security.core.userdetails.User(
                user.getUser_name(),
                user.getPassword(),
                authorities
        );
    }
} 