package com.vn.demo.dao;

import com.vn.demo.entity.Hub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "hub")
public interface HubRepository extends JpaRepository<Hub, Integer> {
}
