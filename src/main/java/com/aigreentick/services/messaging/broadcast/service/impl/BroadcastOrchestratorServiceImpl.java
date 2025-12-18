package com.aigreentick.services.messaging.broadcast.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.messaging.broadcast.client.dto.Template;
import com.aigreentick.services.messaging.broadcast.client.dto.User;
import com.aigreentick.services.messaging.broadcast.client.dto.WhatsappAccount;
import com.aigreentick.services.messaging.broadcast.client.service.TemplateService;
import com.aigreentick.services.messaging.broadcast.client.service.UserService;
import com.aigreentick.services.messaging.broadcast.dto.BroadcastRequest;
import com.aigreentick.services.messaging.broadcast.dto.build.BuildTemplate;
import com.aigreentick.services.messaging.broadcast.kafka.event.BroadcastReportEvent;
import com.aigreentick.services.messaging.broadcast.kafka.producer.BroadcastReportProducer;
import com.aigreentick.services.messaging.broadcast.model.Broadcast;
import com.aigreentick.services.messaging.report.model.Report;
import com.aigreentick.services.messaging.report.service.impl.ReportServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastOrchestratorServiceImpl {
    private final UserService userService;
    private final TemplateService templateService;
    private final TemplateBuilderService templateBuilderService;
    private final BroadcastReportProducer broadcastReportProducer;
    private final ReportServiceImpl reportService;
    private final BroadcastServiceImpl broadcastServiceImpl;

    /**
     * Main broadcast handler - orchestrates entire broadcast flow
     */
    @Transactional
    public ResponseMessage<BroadcastResult> handleBroadcast(BroadcastRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("=== Starting Broadcast ===");
            log.info("Template: {} | Recipients: {} | CampName: {}", 
                request.getTemplatename(), 
                request.getMobileNumbers().size(), 
                request.getCampName());

            //  Validate and fetch user configuration
            User user = validateAndGetUser();
            WhatsappAccount config = userService.findActiveByUserId(user.getId());
            
            if (config == null) {
                return ResponseMessage.error("No active WhatsApp account found for user");
            }

            //  Validate and fetch template
            Template template = templateService.findByNameAndUserIdNotDeleted(
                    request.getTemplatename(),
                    user.getId());
            
            if (template == null) {
                return ResponseMessage.error("Template not found: " + request.getTemplatename());
            }

            //  Filter and validate mobile numbers
            List<String> validNumbers = filterAndValidateNumbers(request.getMobileNumbers());
            
            if (validNumbers.isEmpty()) {
                return ResponseMessage.error("No valid mobile numbers found");
            }

            log.info("Validated {} out of {} mobile numbers", 
                validNumbers.size(), request.getMobileNumbers().size());

            // Build templates for all recipients
            TemplateValidationResult validationResult = buildAndValidateTemplates(
                    user.getId(), validNumbers, template, request);

            if (validationResult.validTemplates().isEmpty()) {
                return ResponseMessage.error("Failed to build templates for any recipient");
            }

            log.info("Built {} valid templates (Invalid: {})", 
                validationResult.validRecipients().size(),
                validationResult.invalidRecipients().size());

            // 5. Create broadcast record
            Broadcast broadcast = createBroadcast(request, user.getId(), 
                validationResult.validRecipients().size());

            // 6. Create report entries for each recipient
            List<Report> reports = createReportEntries(
                broadcast.getId(), 
                user.getId(), 
                validationResult.validRecipients());

            log.info("Created broadcast record. broadcastId={} totalRecipients={}", 
                broadcast.getId(), reports.size());

            // 7. Publish events to Kafka asynchronously
            CompletableFuture<Void> publishFuture = publishBroadcastEvents(
                    reports,
                    validationResult.validTemplates(),
                    config,
                    broadcast.getId(),
                    user.getId());

            // 8. Return immediately (don't wait for Kafka)
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("=== Broadcast Initiated Successfully ===");
            log.info("BroadcastId: {} | Duration: {}ms | Valid: {} | Invalid: {}", 
                broadcast.getId(), duration,
                validationResult.validRecipients().size(),
                validationResult.invalidRecipients().size());

            BroadcastResult result = new BroadcastResult(
                broadcast.getId(),
                validationResult.validRecipients().size(),
                validationResult.invalidRecipients().size(),
                validationResult.invalidRecipients()
            );

            return ResponseMessage.success(
                "Broadcast initiated successfully. Processing in background.",
                result
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Broadcast failed after {}ms. Error: {}", duration, e.getMessage(), e);
            return ResponseMessage.error("Broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Validate user and return user object
     */
    private User validateAndGetUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        return user;
    }

    /**
     * Filter and validate mobile numbers
     */
    private List<String> filterAndValidateNumbers(List<String> mobileNumbers) {
        return mobileNumbers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(num -> !num.isEmpty())
                .distinct() 
                .collect(Collectors.toList());
    }

    /**
     * Build and validate templates for all recipients
     */
    private TemplateValidationResult buildAndValidateTemplates(
            Long userId, 
            List<String> validNumbers, 
            Template template,
            BroadcastRequest request) {
        
        List<String> validRecipients = new ArrayList<>();
        List<String> invalidRecipients = new ArrayList<>();
        
        try {
            // Build templates using template builder service
            List<BuildTemplate> allTemplates = templateBuilderService
                    .buildSendableTemplates(validNumbers, template, request);

            // Validate each template
            List<BuildTemplate> validTemplates = allTemplates.stream()
                    .filter(req -> validateTemplate(req, validRecipients, invalidRecipients))
                    .collect(Collectors.toList());

            return new TemplateValidationResult(validTemplates, validRecipients, invalidRecipients);

        } catch (Exception e) {
            log.error("Template building failed", e);
            throw new RuntimeException("Template building failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a single template and categorizes the recipient
     */
    private boolean validateTemplate(
            BuildTemplate request,
            List<String> validRecipients,
            List<String> invalidRecipients) {

        try {
            if (request.getTemplate() == null) {
                log.warn("Template not built for recipient: {}", request.getTo());
                invalidRecipients.add(request.getTo());
                return false;
            }

            if (request.getTemplate().getName() == null ||
                    request.getTemplate().getLanguage() == null) {
                log.warn("Incomplete template for recipient: {}", request.getTo());
                invalidRecipients.add(request.getTo());
                return false;
            }

            validRecipients.add(request.getTo());
            return true;

        } catch (Exception e) {
            log.error("Validation failed for recipient: {}", request.getTo(), e);
            invalidRecipients.add(request.getTo());
            return false;
        }
    }

    /**
     * Create broadcast record in database
     */
    private Broadcast createBroadcast(BroadcastRequest request, Long userId, int totalRecipients) {
        Broadcast broadcast = Broadcast.builder()
                .userId(userId)
                .templateId(request.getTemplateId())
                .countryId(request.getCountryId())
                .campname(request.getCampName())
                .isMedia(request.getIsMedia() != null ? request.getIsMedia().toString() : "false")
                .total(totalRecipients)
                .status("PROCESSING")
                .build();

        return broadcastServiceImpl.save(broadcast);
       
    }

    /**
     * Create report entries for each recipient
     */
    private List<Report> createReportEntries(
            Long broadcastId, 
            Long userId, 
            List<String> recipients) {
        
        List<Report> reports = new ArrayList<>();
        
        for (String mobile : recipients) {
            Report report = Report.builder()
                    .userId(userId)
                    .broadcastId(broadcastId)
                    .mobile(mobile)
                    .type("TEMPLATE")
                    .status("PENDING")
                    .messageStatus("PENDING")
                    .paymentStatus(0)
                    .platform("WHATSAPP")
                    .build();

            // Save each report
            Report saved = reportService.save(report);
            reports.add(saved);
        }
        
        log.info("Created {} report entries for broadcastId={}", reports.size(), broadcastId);
        
        return reports;
    }

    /**
     * Publish broadcast events to Kafka
     */
    private CompletableFuture<Void> publishBroadcastEvents(
            List<Report> reports,
            List<BuildTemplate> templates,
            WhatsappAccount config,
            Long broadcastId, 
            Long userId) {

        // Create map for fast lookup
        Map<String, BuildTemplate> templateMap = templates.stream()
                .collect(Collectors.toMap(BuildTemplate::getTo, t -> t, (a, b) -> a));

        // Create Kafka events
        List<BroadcastReportEvent> events = reports.stream()
                .map(report -> createBroadcastReportEvent(report, templateMap, config, broadcastId, userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("Publishing {} events to Kafka for broadcastId={}", events.size(), broadcastId);

        // Publish batch asynchronously
        CompletableFuture<Void> publishFuture = broadcastReportProducer.publishBatch(events);

        publishFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish events to Kafka. broadcastId={}", broadcastId, ex);
            } else {
                log.info("Successfully published {} events to Kafka. broadcastId={}", 
                    events.size(), broadcastId);
            }
        });

        return publishFuture;
    }

    /**
     * Create a single broadcast event
     */
    private BroadcastReportEvent createBroadcastReportEvent(
            Report report,
            Map<String, BuildTemplate> templateMap,
            WhatsappAccount config,
            Long broadcastId,
            Long userId) {

        BuildTemplate template = templateMap.get(report.getMobile());
        if (template == null) {
            log.warn("No template found for recipient: {}", report.getMobile());
            return null;
        }

        return BroadcastReportEvent.create(
                broadcastId,
                report.getId(),
                userId,
                config.getWhatsappNoId(),
                config.getParmenentToken(),
                report.getMobile(),
                template);
    }

    /**
     * Result of template validation process
     */
    private record TemplateValidationResult(
            List<BuildTemplate> validTemplates,
            List<String> validRecipients,
            List<String> invalidRecipients) {
    }

    /**
     * Response message wrapper
     */
    public static class ResponseMessage<T> {
        private final String status;
        private final String message;
        private final T data;

        private ResponseMessage(String status, String message, T data) {
            this.status = status;
            this.message = message;
            this.data = data;
        }

        public static <T> ResponseMessage<T> success(String message, T data) {
            return new ResponseMessage<>("SUCCESS", message, data);
        }

        public static <T> ResponseMessage<T> error(String message) {
            return new ResponseMessage<>("ERROR", message, null);
        }

        // Getters
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public T getData() { return data; }
    }

    /**
     * Broadcast result data
     */
    public record BroadcastResult(
            Long broadcastId,
            int validCount,
            int invalidCount,
            List<String> invalidRecipients) {
    }
}