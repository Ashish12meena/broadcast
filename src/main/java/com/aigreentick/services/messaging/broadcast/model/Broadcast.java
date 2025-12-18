package com.aigreentick.services.messaging.broadcast.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aigreentick.services.messaging.broadcast.enums.BroadcastStatus;
import com.aigreentick.services.messaging.broadcast.enums.IsMediaStatus;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "broadcasts")
@Builder
public class Broadcast {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "template_id", nullable = false)
    private Long templateId;
    
    @Column(name = "whatsapp")
    private Integer whatsapp;
    
    @Column(name = "country_id", nullable = false)
    private Long countryId;
    
    @Column(name = "campname", nullable = false)
    private String campname;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "is_media", nullable = false)
    @Builder.Default
    private IsMediaStatus isMedia = IsMediaStatus.NO;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "JSON")
    private String data;
    
    @Column(name = "total", nullable = false)
    @Builder.Default
    private Integer total = 0;
    
    @Column(name = "schedule_at")
    private LocalDateTime scheduleAt;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BroadcastStatus status = BroadcastStatus.PENDING;
    
    @Column(name = "numbers", columnDefinition = "LONGTEXT")
    private String numbers;
    
    @Column(name = "requests", columnDefinition = "LONGTEXT")
    private String requests;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}