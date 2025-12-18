package com.aigreentick.services.messaging.broadcast.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.aigreentick.services.messaging.broadcast.enums.BroadcastStatus;

@Entity
@Table(name = "broadcast_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BroadcastLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "broadcast_id", nullable = false)
    private Long broadcastId;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "wa_id", length = 255)
    private String waId;

    @Column(name = "message_status", nullable = false, length = 50)
    private String messageStatus;

    @Column(nullable = false)
    private BroadcastStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
