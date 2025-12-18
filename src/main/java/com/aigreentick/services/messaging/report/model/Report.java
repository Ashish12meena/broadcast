package com.aigreentick.services.messaging.report.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
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

    @Column(nullable = false)
    private String mobile;

    @Column(nullable = false)
    private String type;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "wa_id")
    private String waId;

    @Column(name = "message_status")
    private String messageStatus;

    @Column(nullable = false, length = 522)
    private String status;

    @Lob
    private String payload;

    @Column(name = "payment_status", nullable = false)
    private Integer paymentStatus;

    @Column(columnDefinition = "json")
    private String response;

    @Column(columnDefinition = "json")
    private String contact;

    @Column(nullable = false)
    private String platform;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

