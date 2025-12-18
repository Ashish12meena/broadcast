package com.aigreentick.services.messaging.report.service.impl;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.report.model.Report;
import com.aigreentick.services.messaging.report.repository.ReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl {
    private final ReportRepository reportRepository;

    public void save(Report report) {
        reportRepository.save(report);
    }

    public int updateReportMessage(Long broadcastReportId, String payload, String responseJson, MessageStatus status,
            String whatsappMessageId, LocalDateTime now) {
       return reportRepository.updateReportMessage(broadcastReportId,payload,responseJson,status,whatsappMessageId,now);
    }
}
