package com.vn.demo.dao;

import com.vn.demo.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "device")
public interface DeviceRepository extends JpaRepository<Device, Integer> {
}
