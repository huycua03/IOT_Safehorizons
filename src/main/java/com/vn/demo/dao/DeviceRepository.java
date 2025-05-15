package com.vn.demo.dao;

import com.vn.demo.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Integer> {
    
    // Tìm thiết bị theo tên
    List<Device> findByDeviceName(String deviceName);
}
