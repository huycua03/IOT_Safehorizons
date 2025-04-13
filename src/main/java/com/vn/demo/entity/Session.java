package com.vn.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Tự động tăng
    @Column(name = "session_id")
    private int sessionId;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status")
    private String status;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Log> logs;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DeviceStatusHistory> deviceStatusHistory;
}