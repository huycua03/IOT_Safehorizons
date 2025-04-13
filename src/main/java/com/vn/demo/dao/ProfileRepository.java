package com.vn.demo.dao;

import com.vn.demo.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "profile")
public interface ProfileRepository extends JpaRepository<Profile, Integer> {
}
