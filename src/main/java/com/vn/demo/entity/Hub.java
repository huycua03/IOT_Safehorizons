package com.vn.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "hub")
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    @Column(name = "hub_id")
    private int hubId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "location")
    private String location;

    @OneToMany(mappedBy = "hub", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Device> devices;

    @OneToMany(mappedBy = "hub", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Assignment> assignments;
}