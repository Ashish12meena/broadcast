package com.aigreentick.services.messaging.broadcast.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

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

    @Column(nullable = false)
    private String mobile;

    @Column(nullable = false)
    private String type;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "wa_id")
    private String waId;

    // REMOVED @JdbcTypeCode - this is VARCHAR in database
    @Column(name = "message_status", nullable = false)
    private String messageStatus;

    // KEEP @JdbcTypeCode - this is ENUM in database
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}