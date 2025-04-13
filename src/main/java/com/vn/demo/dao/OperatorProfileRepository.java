package com.vn.demo.dao;

import com.vn.demo.entity.OperatorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "operatorprofile")
public interface OperatorProfileRepository extends JpaRepository<OperatorProfile, Integer> {
}
