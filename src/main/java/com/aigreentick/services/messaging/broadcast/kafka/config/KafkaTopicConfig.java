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

    @Value("${kafka.topics.campaign-messages.partitions:50}") // INCREASED from 20 to 50
    private int broadcastReportPartitions;

    @Value("${kafka.topics.campaign-messages.replicas:2}")
    private int broadcastReportReplicas;

    // @Value("${kafka.topics.campaign-lifecycle.name}")
    // private String campaignLifecycleTopicName;

    // @Value("${kafka.topics.campaign-lifecycle.partitions:10}")
    // private int campaignLifecyclePartitions;

    // @Value("${kafka.topics.campaign-lifecycle.replicas:2}")
    // private int campaignLifecycleReplicas;

    // @Value("${kafka.topics.campaign-status.name}")
    // private String campaignStatusTopicName;

    // @Value("${kafka.topics.campaign-status.partitions:10}")
    // private int campaignStatusPartitions;

    // @Value("${kafka.topics.campaign-status.replicas:2}")
    // private int campaignStatusReplicas;

    // @Value("${kafka.topics.campaign-dlq.name}")
    // private String campaignDlqTopicName;

    /**
     * Topic for campaign message events (high throughput).
     * Partitioned by campaignId for parallel processing.
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

        log.info("Campaign Messages Topic Configuration:");
        log.info("  - Name: {}", broadcastReportTopicName);
        log.info("  - Partitions: {} (INCREASED for high throughput)", broadcastReportPartitions);
        log.info("  - Replicas: {}", broadcastReportReplicas);
        log.info("  - Max message size: 2MB");
        log.info("  - Retention: 7 days");

        return topic;
    }
}
