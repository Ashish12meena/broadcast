package com.aigreentick.services.messaging.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.aigreentick.services.messaging.broadcast.kafka.event.BroadcastReportEvent;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class KafkaConfiguration {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.concurrency:50}")

    private int consumerConcurrency;

    // ==================== PRODUCER CONFIGURATION ====================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // // Enable type info headers so consumers know the actual type
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);

        // // High throughput settings
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        config.put(ProducerConfig.LINGER_MS_CONFIG, "50");
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, "131072");
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "268435456");
        config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "2097152");

        // // Reliability
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, "3");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        // log.info("Kafka Producer initialized with type headers ENABLED");

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ==================== CAMPAIGN MESSAGE CONSUMER ====================

    /**
     * Specific consumer factory for CampaignMessageEvent
     */
    @Bean
    public ConsumerFactory<String, BroadcastReportEvent> campaignMessageConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        //  Specific type for this consumer
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aigreentick.services.messaging.*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, BroadcastReportEvent.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        // High throughput settings
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "2048");
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "100");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        log.info("Campaign Message Consumer Factory initialized for CampaignMessageEvent");

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(BroadcastReportEvent.class));
    }

    /**
     * Listener factory specifically for campaign messages
     */

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BroadcastReportEvent> campaignKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, BroadcastReportEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(campaignMessageConsumerFactory());
        factory.setConcurrency(consumerConcurrency); // Match partition count

        // MANUAL ack mode allows async processing before acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        log.info("Campaign Kafka Listener Factory initialized:");
        log.info("  - Type: CampaignMessageEvent");
        log.info("  - Concurrency: {} consumers", consumerConcurrency);
        log.info("  - Ack Mode: MANUAL (async processing)");

        return factory;
    }
}
