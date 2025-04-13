package com.vn.demo.dao;

import com.vn.demo.entity.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "command")
public interface CommandRepository extends JpaRepository<Command, Integer> {
}
