package com.aigreentick.services.messaging.broadcast.service.impl;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.broadcast.model.Report;
import com.aigreentick.services.messaging.broadcast.repository.ReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl {
    private final ReportRepository reportRepository;

    /**
     * Update report by broadcastId + mobile (for dispatch flow)
     * Used when reports are created by another service
     */
    @Transactional
    public int updateReportByBroadcastIdAndMobile(
            Long broadcastId,
            String mobile,
            String responseJson, 
            MessageStatus messageStatus,
            String whatsappMessageId, 
            LocalDateTime now) {
        
        try {
            // Convert MessageStatus enum to string value
            String statusValue = messageStatus.getValue();
            
            int updated = reportRepository.updateReportByBroadcastIdAndMobile(
                    broadcastId,
                    mobile,
                    responseJson, 
                    statusValue, // status field
                    statusValue, // messageStatus field (both same)
                    whatsappMessageId, 
                    now);

            if (updated == 0) {
                log.warn("No report found to update. broadcastId={} mobile={}", 
                    broadcastId, mobile);
            } else {
                log.debug("Updated report. broadcastId={} mobile={} status={} messageId={}", 
                    broadcastId, mobile, statusValue, whatsappMessageId);
            }

            return updated;

        } catch (Exception e) {
            log.error("Failed to update report by broadcastId+mobile. broadcastId={} mobile={}", 
                broadcastId, mobile, e);
            throw e;
        }
    }

    /**
     * Find report by ID (useful for debugging)
     */
    @Transactional(readOnly = true)
    public Report findById(Long id) {
        return reportRepository.findById(id).orElse(null);
    }
}