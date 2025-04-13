package com.vn.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Time;
import java.util.Date;

@Entity
@Data
@Table(name = "assignment")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    @Column(name = "assignment_id")
    private int assignmentId;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    private User user;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "profile_id", referencedColumnName = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "hub_id", referencedColumnName = "hub_id", nullable = false)
    private Hub hub;

    @Column(name = "assignment_date")
    private Date assignmentDate;

    @Column(name = "time_slot_start")
    private Time timeSlotStart;

    @Column(name = "time_slot_end")
    private Time timeSlotEnd;

    @Column(name = "status")
    private String status;
}