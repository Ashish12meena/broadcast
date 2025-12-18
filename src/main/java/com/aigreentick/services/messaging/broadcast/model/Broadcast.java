    package com.aigreentick.services.messaging.broadcast.model;

    import jakarta.persistence.*;
    import lombok.*;

    import java.time.LocalDateTime;

import com.aigreentick.services.messaging.broadcast.enums.BroadcastStatus;

    @Entity
    @Table(name = "broadcasts")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class Broadcast {

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

        @Column(columnDefinition = "json")
        private String data;

        @Column(nullable = false)
        private Integer total;

        @Column(name = "schedule_at")
        private LocalDateTime scheduleAt;

        @Column(nullable = false)
        private BroadcastStatus status;

        @Lob
        @Column(columnDefinition = "longtext")
        private String numbers;

        @Lob
        @Column(columnDefinition = "longtext")
        private String requests;

        @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
        private LocalDateTime createdAt;

        @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
        private LocalDateTime updatedAt;

        @Column(name = "deleted_at")
        private LocalDateTime deletedAt;
    }
