package com.aigreentick.services.messaging.campaigns.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.aigreentick.services.messaging.broadcast.enums.CampaignStatus;

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

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "wa_id", length = 255)
    private String waId;

    @Column(name = "message_status", length = 100)
    private String messageStatus;

    @Column(nullable = false)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.FAILED;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
