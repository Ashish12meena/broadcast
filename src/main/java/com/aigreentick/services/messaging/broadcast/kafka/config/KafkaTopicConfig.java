package com.aigreentick.services.messaging.broadcast.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class KafkaTopicConfig {
    
    @Value("${kafka.topics.campaign-messages.name}")
    private String broadcastReportTopicName;

    @Value("${kafka.topics.campaign-messages.partitions:50}")
    private int broadcastReportPartitions;

    @Value("${kafka.topics.campaign-messages.replicas:2}")
    private int broadcastReportReplicas;

    @Value("${kafka.topics.campaign-dlq.name}")
    private String campaignDlqTopicName;

    @Value("${kafka.topics.campaign-dlq.partitions:10}")
    private int campaignDlqPartitions;

    @Value("${kafka.topics.campaign-dlq.replicas:2}")
    private int campaignDlqReplicas;

    /**
     * Main topic for broadcast message events (high throughput).
     * Partitioned by broadcastId for parallel processing.
     */
    @Bean
    public NewTopic broadcastReportTopic() {
        NewTopic topic = TopicBuilder.name(broadcastReportTopicName)
                .partitions(broadcastReportPartitions)
                .replicas(broadcastReportReplicas)
                .config("retention.ms", "604800000") // 7 days
                .config("compression.type", "snappy")
                .config("max.message.bytes", "2097152") // 2MB
                .config("min.insync.replicas", "1")
                .config("segment.bytes", "1073741824") // 1GB segments
                .build();

        log.info("=== Broadcast Messages Topic Configuration ===");
        log.info("  - Name: {}", broadcastReportTopicName);
        log.info("  - Partitions: {} (for parallel processing)", broadcastReportPartitions);
        log.info("  - Replicas: {}", broadcastReportReplicas);
        log.info("  - Max message size: 2MB");
        log.info("  - Retention: 7 days");
        log.info("  - Compression: snappy");

        return topic;
    }

    /**
     * Dead Letter Queue topic for failed messages.
     * Used for messages that fail after all retries.
     */
    @Bean
    public NewTopic campaignDlqTopic() {
        NewTopic topic = TopicBuilder.name(campaignDlqTopicName)
                .partitions(campaignDlqPartitions)
                .replicas(campaignDlqReplicas)
                .config("retention.ms", "2592000000") // 30 days
                .config("compression.type", "snappy")
                .config("max.message.bytes", "5242880") // 5MB (larger for error details)
                .config("min.insync.replicas", "1")
                .build();

        log.info("=== Dead Letter Queue Topic Configuration ===");
        log.info("  - Name: {}", campaignDlqTopicName);
        log.info("  - Partitions: {}", campaignDlqPartitions);
        log.info("  - Replicas: {}", campaignDlqReplicas);
        log.info("  - Max message size: 5MB");
        log.info("  - Retention: 30 days (for manual review)");

        return topic;
    }
}