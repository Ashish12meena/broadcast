package com.aigreentick.services.messaging.broadcast.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aigreentick.services.messaging.broadcast.enums.Platform;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reports")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "broadcast_id")
    private Long broadcastId;
    
    @Column(name = "campaign_id")
    private Long campaignId;
    
    @Column(name = "group_send_id")
    private Long groupSendId;
    
    @Column(name = "tag_log_id")
    private Long tagLogId;
    
    @Column(name = "mobile", nullable = false, length = 20)
    private String mobile;
    
    @Column(name = "type", nullable = false, length = 50)
    private String type = "template";
    
    @Column(name = "message_id")
    private String messageId;
    
    @Column(name = "wa_id")
    private String waId;
    
    @Column(name = "message_status")
    private String messageStatus;
    
    @Column(name = "status", nullable = false, length = 522)
    private String status;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", columnDefinition = "JSON")
    private String response;

    private String payload;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact", columnDefinition = "JSON")
    private String contact;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private Platform platform = Platform.web;
    
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
