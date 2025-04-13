package com.vn.demo.dao;

import com.vn.demo.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "role")
public interface RoleRepository extends JpaRepository<Role, Integer>{
}
