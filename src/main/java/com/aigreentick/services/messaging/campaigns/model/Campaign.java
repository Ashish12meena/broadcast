package com.aigreentick.services.messaging.campaigns.model;



import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.aigreentick.services.messaging.broadcast.enums.CampaignStatus;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column
    private Integer whatsapp;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Column(name = "campname", nullable = false, length = 255)
    private String campname;

    @Column(name = "is_media", nullable = false)
    private Boolean isMedia;

    @Column(name = "col_name", length = 255)
    private String colName;

    @Column(nullable = false)
    private Integer total;

    @Column(nullable = false)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.FAILED;

    // TIMESTAMP NULL DEFAULT NULL
    @Column(name = "schedule_at")
    private LocalDateTime scheduleAt;

    // TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // TIMESTAMP NULL DEFAULT NULL
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
