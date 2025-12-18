package com.aigreentick.services.messaging.broadcast.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

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

    public ResponseMessage<Long> handleBroadcast(BroadcastRequest request) {
        User user = new User();
        user.setId(1L);

        WhatsappAccount config = userService.findActiveByUserId(user.getId());

        Template template = templateService.findByNameAndUserIdNotDeleted(
                request.getTemplatename(),
                user.getId());

        List<String> validNumbers = filterAndValidateNumbers(request.getMobileNumbers());

        TemplateValidationResult validationResult = buildAndValidateTemplates(
                user.getId(), validNumbers, template, request);

        Broadcast broadcast = createBroadcaste();

        List<Report> reports = buildAndSaveReports();

        // Publish to Kafka
        publishBroadcastEvents(
                reports,
                validationResult.validTemplates(),
                config,
                broadcast.getId(),
                user.getId());

        log.info("Broadcast initiated. broadcastId={} reports={}",
                broadcast.getId(), reports.size());

        return new ResponseMessage<>(
                "Success",
                "Campaign initiated successfully. Valid: " + validationResult.validRecipients().size() +
                        ", Invalid: " + validationResult.invalidRecipients().size(),
                broadcast.getId());

    }

    private void publishBroadcastEvents(
            List<Report> reports,
            List<BuildTemplate> templates,
            WhatsappAccount config,
            Long broadcastId, Long userId) {

        Map<String, BuildTemplate> templateMap = templates.stream()
                .collect(Collectors.toMap(BuildTemplate::getTo, t -> t, (a, b) -> a));

        List<BroadcastReportEvent> events = reports.stream()
                .map(msg -> createBroadcastReportEvent(msg, templateMap, config, broadcastId, userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

          log.info("Created {} Kafka events for campaign {}", events.size(), broadcastId);

        CompletableFuture<Void> publishFuture = broadcastReportProducer.publishBatch(events);

        publishFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish campaign events. campaignId={}", broadcastId, ex);
            } else {
                log.info("Successfully published {} events to Kafka. campaignId={}",
                        events.size(), broadcastId);
            }
        });
    }

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

    private List<Report> buildAndSaveReports() {
        return List.of();
    }

    private TemplateValidationResult buildAndValidateTemplates(Long id, List<String> validNumbers, Template template,
            BroadcastRequest request) {
        List<String> validRecipients = new ArrayList<>();
        List<String> invalidRecipients = new ArrayList<>();
        try {
            List<BuildTemplate> allTemplates = templateBuilderService
                    .buildSendableTemplates(validNumbers, template, request);

            List<BuildTemplate> validTemplates = allTemplates.stream()
                    .filter(req -> validateTemplate(req, validRecipients, invalidRecipients))
                    .collect(Collectors.toList());

            return new TemplateValidationResult(validTemplates, validRecipients, invalidRecipients);

        } catch (Exception e) {
            throw new RuntimeException("Template building failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a single template and categorizes the recipient.
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

    private List<String> filterAndValidateNumbers(List<String> mobileNumbers) {
        return List.of();
    }

    private Broadcast createBroadcaste() {
        Broadcast broadcast = new Broadcast();
        return broadcast;
    }

    /**
     * Result of template validation process.
     */
    private record TemplateValidationResult(
            List<BuildTemplate> validTemplates,
            List<String> validRecipients,
            List<String> invalidRecipients) {
    }

    private record ResponseMessage<T>(
            String status,
            String message,
            T data) {
    }
}
