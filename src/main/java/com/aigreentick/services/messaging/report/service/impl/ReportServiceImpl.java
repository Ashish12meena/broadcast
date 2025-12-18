package com.aigreentick.services.messaging.report.service.impl;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.report.model.Report;
import com.aigreentick.services.messaging.report.repository.ReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl {
    private final ReportRepository reportRepository;

    /**
     * Save new report entry
     */
    @Transactional
    public Report save(Report report) {
        try {
            Report saved = reportRepository.save(report);
            log.debug("Saved report. id={} mobile={}", saved.getId(), saved.getMobile());
            return saved;
        } catch (Exception e) {
            log.error("Failed to save report. mobile={}", report.getMobile(), e);
            throw e;
        }
    }

    /**
     * Update report message with WhatsApp API response.
     * Converts MessageStatus enum to string for database.
     */
    @Transactional
    public int updateReportMessage(
            Long reportId, 
            String responseJson, 
            MessageStatus messageStatus,
            String whatsappMessageId, 
            LocalDateTime now) {
        
        try {
            // Convert MessageStatus enum to string value
            String statusValue = messageStatus.getValue();
            
            int updated = reportRepository.updateReportMessage(
                    reportId, 
                    responseJson, 
                    statusValue, // status field
                    statusValue, // messageStatus field (both same)
                    whatsappMessageId, 
                    now);

            if (updated == 0) {
                log.warn("No report found to update. reportId={}", reportId);
            } else {
                log.debug("Updated report. reportId={} status={} messageId={}", 
                    reportId, statusValue, whatsappMessageId);
            }

            return updated;

        } catch (Exception e) {
            log.error("Failed to update report message. reportId={}", reportId, e);
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