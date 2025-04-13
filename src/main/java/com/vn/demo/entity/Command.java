package com.vn.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "command")
public class Command {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    @Column(name = "command_id")
    private int commandId;

    @Column(name = "command_text")
    private String commandText;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "command_list_id", referencedColumnName = "command_list_id", nullable = false)
    private CommandList commandList;
}