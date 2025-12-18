package com.aigreentick.services.messaging.broadcast.client.dto;

import jakarta.persistence.*;
import lombok.Data;

@Data
public class Blacklist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    private String mobile;
}