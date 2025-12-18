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
     * Update report message with WhatsApp API response
     * Uses optimized bulk update query
     */
    @Transactional
    public int updateReportMessage(
            Long broadcastReportId, 
            String payload, 
            String responseJson, 
            MessageStatus status,
            String whatsappMessageId, 
            LocalDateTime now) {
        
        try {
            int updated = reportRepository.updateReportMessage(
                    broadcastReportId, 
                    payload, 
                    responseJson, 
                    status,
                    whatsappMessageId, 
                    now);

            if (updated == 0) {
                log.warn("No report found to update. reportId={}", broadcastReportId);
            } else {
                log.debug("Updated report. reportId={} status={} messageId={}", 
                    broadcastReportId, status, whatsappMessageId);
            }

            return updated;

        } catch (Exception e) {
            log.error("Failed to update report message. reportId={}", broadcastReportId, e);
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