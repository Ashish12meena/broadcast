package com.aigreentick.services.messaging.broadcast.client.service.impl;


import java.net.URI;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.aigreentick.services.messaging.broadcast.client.config.WhatsappClientProperties;
import com.aigreentick.services.messaging.broadcast.client.dto.FacebookApiResponse;
import com.aigreentick.services.messaging.broadcast.client.service.WhatsappClientService;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Real implementation that calls Facebook WhatsApp Business API.
 * Active when profile is NOT 'stress-test' or 'mock'.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Profile("!stress-test & !mock")
public class WhatsappClientRealImpl implements WhatsappClientService {

    private final WebClient.Builder webClientBuilder;
    private final WhatsappClientProperties properties;

    @Override
    public FacebookApiResponse<SendTemplateMessageResponse> sendMessage(
            String bodyJson, 
            String phoneNumberId, 
            String accessToken) {

        if (!properties.isOutgoingEnabled()) {
            return FacebookApiResponse.error("Outgoing requests disabled", 503);
        }

        URI uri = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment(properties.getApiVersion(), phoneNumberId, "messages")
                .build()
                .toUri();

        try {
            SendTemplateMessageResponse response = webClientBuilder.build()
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .bodyValue(bodyJson)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            r -> Mono.error(new RuntimeException("Facebook API returned 4xx")))
                    .onStatus(HttpStatusCode::is5xxServerError,
                            r -> Mono.error(new RuntimeException("Facebook API returned 5xx")))
                    .bodyToMono(SendTemplateMessageResponse.class)
                    .block();

            log.info("Template message sent. PHONE_NUMBER_ID={} Response={}", phoneNumberId, response);
            return FacebookApiResponse.success(response, 200);

        } catch (WebClientResponseException ex) {
            log.error("Failed to send message. PHONE_NUMBER_ID={} Status={} Response={}",
                    phoneNumberId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

        } catch (Exception ex) {
            log.error("Unexpected error while sending message. PHONE_NUMBER_ID={}", phoneNumberId, ex);
            return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
        }
    }
}   