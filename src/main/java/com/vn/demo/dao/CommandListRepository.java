package com.vn.demo.dao;

import com.vn.demo.entity.CommandList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "commandlist")
public interface CommandListRepository extends JpaRepository<CommandList, Integer> {
}
