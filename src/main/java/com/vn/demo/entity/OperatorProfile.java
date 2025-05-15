package com.vn.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "operator_profile")
public class OperatorProfile {

    @EmbeddedId
    private OperatorProfileId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("profileId") // Ánh xạ profileId từ composite key
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("operatorId") // Ánh xạ operatorId từ composite key
    @JoinColumn(name = "operator_id")
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}