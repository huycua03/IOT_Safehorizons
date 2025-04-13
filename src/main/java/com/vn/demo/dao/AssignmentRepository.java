package com.vn.demo.dao;

import com.vn.demo.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "assignment")
public interface AssignmentRepository extends JpaRepository<Assignment, Integer> {
}
