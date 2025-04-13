package com.vn.demo.dao;

import com.vn.demo.entity.Log;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "log")
public interface LogRepository extends JpaRepository<Log, Integer> {
}
