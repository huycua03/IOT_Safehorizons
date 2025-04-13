package com.vn.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "command_list")
public class CommandList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    @Column(name = "command_list_id")
    private int commandListId;

    @Column(name = "name")
    private String name;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private User teamLead;

    @OneToMany(mappedBy = "commandList", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Profile> profiles;

    @OneToMany(mappedBy = "commandList", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Command> commands;
}