package com.aigreentick.services.messaging.report.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.report.model.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                UPDATE Report r
                SET r.payload = :payload,
                    r.response = :response,
                    r.status = :status,
                    r.messageId = COALESCE(:messageId, r.messageId),
                    r.updatedAt = :updatedAt
                WHERE r.id = :id
            """)
    int updateReportMessage(
            @Param("id") Long id,
            @Param("payload") String payload,
            @Param("response") String response,
            @Param("status") MessageStatus status,
            @Param("messageId") String messageId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
