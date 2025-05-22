package com.vn.demo.dao;

import com.vn.demo.entity.Session;
import com.vn.demo.entity.Log;
import com.vn.demo.entity.DeviceStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.LocalDateTime;
import java.util.List;

@RepositoryRestResource(path = "session")
public interface SessionRepository extends JpaRepository<Session, Integer> {
    
    List<Session> findByStatus(String status);
    
    @Query("SELECT s FROM Session s WHERE s.status = 'ACTIVE'")
    List<Session> findActiveSessions();
    
    @Query("SELECT s FROM Session s WHERE s.operator.user_id = :operatorId")
    List<Session> findByOperatorId(@Param("operatorId") Integer operatorId);
    
    @Query("SELECT s FROM Session s WHERE s.device.deviceId = :deviceId")
    List<Session> findByDeviceId(@Param("deviceId") Integer deviceId);
    
    @Query("SELECT s FROM Session s WHERE s.supervisor.user_id = :supervisorId")
    List<Session> findBySupervisorId(@Param("supervisorId") Integer supervisorId);
    
    @Query("SELECT s FROM Session s WHERE s.device.deviceId = :deviceId " +
           "AND s.startTime <= :timestamp " +
           "AND (s.endTime IS NULL OR s.endTime >= :timestamp)")
    List<Session> findByDeviceIdAndActiveTimestamp(@Param("deviceId") Integer deviceId, 
                                                  @Param("timestamp") LocalDateTime timestamp);
    
    @Query("SELECT l FROM Log l WHERE l.session.sessionId = :sessionId ORDER BY l.timestamp DESC")
    List<Log> findLogsById(@Param("sessionId") Integer sessionId);
    
    @Query("SELECT h FROM DeviceStatusHistory h WHERE h.session.sessionId = :sessionId ORDER BY h.timestamp DESC")
    List<DeviceStatusHistory> findDeviceStatusHistoryById(@Param("sessionId") Integer sessionId);
    
    @Query("SELECT s FROM Session s ORDER BY s.startTime DESC")
    List<Session> findAllSessionsOrderByStartTimeDesc();
}
