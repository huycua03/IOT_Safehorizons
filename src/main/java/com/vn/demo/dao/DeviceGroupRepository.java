package com.vn.demo.dao;

import com.vn.demo.entity.DeviceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "devicegroup")
public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Integer> {
}
