package com.vn.demo.dao;

import com.vn.demo.entity.CommandList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommandListRepository extends JpaRepository<CommandList, Integer> {
}
