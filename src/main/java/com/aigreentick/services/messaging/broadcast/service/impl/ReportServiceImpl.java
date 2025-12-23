package com.aigreentick.services.messaging.broadcast.service.impl;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.broadcast.model.Report;
import com.aigreentick.services.messaging.broadcast.repository.ReportRepository;
import com.aigreentick.services.messaging.broadcast.service.impl.BatchCoordinator.DatabaseUpdate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl {

    private final ReportRepository reportRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Batch update multiple reports in SINGLE transaction.
     * Uses JDBC batch update for maximum performance.
     * 
     * @param updates List of database updates
     * @return Number of successfully updated records
     */
    @Transactional
    public int batchUpdateReports(List<DatabaseUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        String sql = """
                    UPDATE reports
                    SET response = ?,
                        status = ?,
                        message_status = ?,
                        message_id = COALESCE(?, message_id),
                        payload = ?,
                        updated_at = ?
                    WHERE broadcast_id = ?
                      AND mobile = ?
                """;

        try {
            // Use BatchPreparedStatementSetter for proper batch processing
            int[] updateCounts = jdbcTemplate.batchUpdate(sql,
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                            DatabaseUpdate update = updates.get(i);
                            ps.setString(1, update.responseJson());
                            ps.setString(2, update.status());
                            ps.setString(3, update.messageStatus());
                            ps.setString(4, update.whatsappMessageId());
                            ps.setString(5, update.payload());
                            ps.setTimestamp(6, Timestamp.valueOf(update.timestamp()));
                            ps.setLong(7, update.broadcastId());
                            ps.setString(8, update.mobile());
                        }

                        @Override
                        public int getBatchSize() {
                            return updates.size();
                        }
                    });

            // Count successful updates
            int successCount = 0;
            int notFoundCount = 0;

            for (int count : updateCounts) {
                if (count > 0) {
                    successCount++;
                } else if (count == 0) {
                    notFoundCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            log.info("Batch update completed: Success={}, NotFound={}, Total={}, Duration={}ms",
                    successCount, notFoundCount, updates.size(), duration);

            if (notFoundCount > 0) {
                log.warn("Some reports were not found in database: {}", notFoundCount);
            }

            return successCount;

        } catch (Exception e) {
            log.error("Batch update failed for {} updates", updates.size(), e);
            throw new RuntimeException("Batch update failed", e);
        }
    }

    /**
     * Legacy method for single update (kept for backward compatibility).
     * Consider migrating all callers to use batchUpdateReports instead.
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
            String statusValue = messageStatus.getValue();

            int updated = reportRepository.updateReportByBroadcastIdAndMobile(
                    broadcastId,
                    mobile,
                    responseJson,
                    statusValue,
                    statusValue,
                    whatsappMessageId,
                    now);

            if (updated == 0) {
                log.warn("No report found to update. broadcastId={} mobile={}",
                        broadcastId, mobile);
            } else {
                log.debug("Updated report. broadcastId={} mobile={} status={}",
                        broadcastId, mobile, statusValue);
            }

            return updated;

        } catch (Exception e) {
            log.error("Failed to update report. broadcastId={} mobile={}",
                    broadcastId, mobile, e);
            throw e;
        }
    }

    /**
     * Find report by ID (for debugging).
     */
    @Transactional(readOnly = true)
    public Report findById(Long id) {
        return reportRepository.findById(id).orElse(null);
    }
}