package com.vn.demo.config;

import com.vn.demo.dao.RoleRepository;
import com.vn.demo.entity.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class InitialRoleSetup implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        // Tạo vai trò ROLE_ADMIN nếu chưa tồn tại
        if (roleRepository.findByRoleName("ROLE_ADMIN") == null) {
            Role adminRole = new Role();
            adminRole.setRoleName("ROLE_ADMIN");
            roleRepository.save(adminRole);
            System.out.println("Vai trò ROLE_ADMIN đã được tạo");
        }

        // Tạo vai trò ROLE_TEAMLEAD nếu chưa tồn tại
        if (roleRepository.findByRoleName("ROLE_TEAMLEAD") == null) {
            Role teamLeadRole = new Role();
            teamLeadRole.setRoleName("ROLE_TEAMLEAD");
            roleRepository.save(teamLeadRole);
            System.out.println("Vai trò ROLE_TEAMLEAD đã được tạo");
        }

        // Tạo vai trò ROLE_OPERATOR nếu chưa tồn tại
        if (roleRepository.findByRoleName("ROLE_OPERATOR") == null) {
            Role operatorRole = new Role();
            operatorRole.setRoleName("ROLE_OPERATOR");
            roleRepository.save(operatorRole);
            System.out.println("Vai trò ROLE_OPERATOR đã được tạo");
        }
    }
} 