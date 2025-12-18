package com.aigreentick.services.messaging.campaigns.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "campaigns")
public class Campaign {

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

    @Column(name = "is_media", nullable = false)
    private String isMedia;

    @Column(name = "col_name")
    private String colName;

    @Column(name = "total", nullable = false)
    private Integer total;

    // Check your database - keep @JdbcTypeCode only if ENUM
    @Column(name = "status", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String status;

    @Column(name = "schedule_at")
    private LocalDateTime scheduleAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
