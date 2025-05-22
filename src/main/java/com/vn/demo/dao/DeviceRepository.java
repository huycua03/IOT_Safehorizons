package com.vn.demo.dao;

import com.vn.demo.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Integer> {
    
    // Tìm thiết bị theo tên
    List<Device> findByDeviceName(String deviceName);
    
    // Tìm thiết bị theo Device Group ID
    @Query("SELECT d FROM Device d WHERE d.deviceGroup.groupId = :deviceGroupId")
    List<Device> findByDeviceGroupId(@Param("deviceGroupId") Integer deviceGroupId);
}
