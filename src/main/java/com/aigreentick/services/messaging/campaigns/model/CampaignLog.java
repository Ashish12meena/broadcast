package com.aigreentick.services.messaging.campaigns.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private String mobile;

    @Column(name = "message_id")
    private String messageId;

    @Column(nullable = false)
    private String type;

    @Column(name = "wa_id")
    private String waId;

    // Check your database - remove @JdbcTypeCode if VARCHAR
    @Column(name = "message_status")
    private String messageStatus;

    // Check your database - keep @JdbcTypeCode only if ENUM
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
