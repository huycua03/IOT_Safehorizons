package com.vn.demo.dao;

import com.vn.demo.entity.OperatorProfile;
import com.vn.demo.entity.OperatorProfileId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@RepositoryRestResource(path = "operatorprofile")
public interface OperatorProfileRepository extends JpaRepository<OperatorProfile, OperatorProfileId> {
    
    // Tìm tất cả OperatorProfile có profileId tương ứng
    @Query("SELECT op FROM OperatorProfile op WHERE op.profile.profileId = :profileId")
    List<OperatorProfile> findByProfileId(@Param("profileId") Integer profileId);
}
