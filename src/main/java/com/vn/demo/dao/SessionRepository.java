package com.vn.demo.dao;

import com.vn.demo.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "session")
public interface SessionRepository extends JpaRepository<Session, Integer> {
}
