package com.aigreentick.services.messaging.report.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.aigreentick.services.messaging.broadcast.enums.Platform;

@Entity
@Table(
    name = "reports",
    indexes = {
        @Index(name = "idx_message_id", columnList = "message_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "wa_id", length = 255)
    private String waId;

    @Column(name = "message_status", length = 255)
    private String messageStatus;

    @Column(nullable = false, length = 522)
    private String status;

    @Column(columnDefinition = "json")
    private String response;

    @Column(columnDefinition = "json")
    private String contact;

    @Column(nullable = false)
    private Platform platform;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
