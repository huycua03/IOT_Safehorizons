package com.vn.demo.dao;

import com.vn.demo.entity.DeviceStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@RepositoryRestResource(path = "devicestatushistory")
public interface DeviceStatusHistoryRepository extends JpaRepository<DeviceStatusHistory, Integer> {
}
