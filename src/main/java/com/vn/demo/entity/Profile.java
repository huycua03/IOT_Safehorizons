package com.vn.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "profile")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    @Column(name = "profile_id")
    private int profileId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "command_list_id", referencedColumnName = "command_list_id", nullable = false)
    private CommandList commandList;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id", referencedColumnName = "group_id", nullable = false)
    private DeviceGroup deviceGroup;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "profile", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<OperatorProfile> operatorProfiles;

    @OneToMany(mappedBy = "profile", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Assignment> assignments;
}