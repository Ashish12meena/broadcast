package com.aigreentick.services.messaging.broadcast.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aigreentick.services.messaging.broadcast.model.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {
    
    /**
     * Update report by broadcastId + mobile (for dispatch flow)
     * Reports are created by another service, we just update them
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                UPDATE Report r
                SET r.response = :response,
                    r.status = :status,
                    r.messageStatus = :messageStatus,
                    r.messageId = COALESCE(:messageId, r.messageId),
                    r.updatedAt = :updatedAt
                WHERE r.broadcastId = :broadcastId 
                  AND r.mobile = :mobile
            """)
    int updateReportByBroadcastIdAndMobile(
            @Param("broadcastId") Long broadcastId,
            @Param("mobile") String mobile,
            @Param("response") String response,
            @Param("status") String status,
            @Param("messageStatus") String messageStatus,
            @Param("messageId") String messageId,
            @Param("updatedAt") LocalDateTime updatedAt);
}