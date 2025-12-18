package com.aigreentick.services.messaging.broadcast.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.Template;
import com.aigreentick.services.messaging.broadcast.client.dto.TemplateComponent;
import com.aigreentick.services.messaging.broadcast.client.dto.User;
import com.aigreentick.services.messaging.broadcast.dto.BroadcastRequest;
import com.aigreentick.services.messaging.broadcast.model.Broadcast;
import com.aigreentick.services.messaging.report.model.Report;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateBuilderService {

    private final ObjectMapper objectMapper;

    /**
     * Build sendable templates for all recipients.
     * Implements the exact logic from the controller's message building loop.
     */

    public List<Map<String, Object>> buildSendableTemplates(
            List<String> validNumbers,
            Template template,
            BroadcastRequest request,
            Long userId,
            Broadcast broadcast) {

        List<Map<String, Object>> sendableTemplates = new ArrayList<>();

        String[] variablesArray = request.getVariables() != null
                ? request.getVariables().split(",")
                : new String[0];

        for (String phoneNumber : validNumbers) {
            phoneNumber = phoneNumber.trim();

            Report broadcastLog = new Report();
            broadcastLog.setUserId(userId);
            broadcastLog.setBroadcastId(broadcast.getId());
            broadcastLog.setMobile(phoneNumber);
            broadcastLog.setStatus("pending");
            broadcastLog.setType("template");

            List<TemplateComponent> bodyTexts = template.getComponents();
            List<Map<String, Object>> parameters = new ArrayList<>();

            for (TemplateComponent bodyText : bodyTexts) {
                if (bodyText.getFormat() != null) {
                    if (request.getMediaUrl() != null && !request.getMediaUrl().isEmpty()) {
                        Map<String, Object> headerParam = new HashMap<>();
                        headerParam.put("type", "header");

                        List<Map<String, Object>> paramList = new ArrayList<>();
                        Map<String, Object> mediaParam = new HashMap<>();
                        mediaParam.put("type", request.getMediaType());

                        Map<String, String> mediaObj = new HashMap<>();
                        mediaObj.put("link", request.getMediaUrl());
                        mediaParam.put(request.getMediaType(), mediaObj);

                        paramList.add(mediaParam);
                        headerParam.put("parameters", paramList);
                        parameters.add(headerParam);
                    }
                }
            }

            if (request.getVariables() != null) {
                List<Map<String, String>> vars = new ArrayList<>();
                for (String v : variablesArray) {
                    Map<String, String> varMap = new HashMap<>();
                    varMap.put("type", "text");
                    varMap.put("text", v);
                    vars.add(varMap);
                }

                Map<String, Object> bodyParam = new HashMap<>();
                bodyParam.put("type", "body");
                bodyParam.put("parameters", vars);
                parameters.add(bodyParam);

                if ("AUTHENTICATION".equalsIgnoreCase(template.getCategory().trim())) {
                    Map<String, Object> buttonParam = new HashMap<>();
                    buttonParam.put("type", "button");
                    buttonParam.put("sub_type", "url");
                    buttonParam.put("index", "0");

                    List<Map<String, String>> buttonParams = new ArrayList<>();
                    Map<String, String> textParam = new HashMap<>();
                    textParam.put("type", "text");
                    textParam.put("text", vars.isEmpty() ? "" : vars.get(0).get("text"));
                    buttonParams.add(textParam);

                    buttonParam.put("parameters", buttonParams);
                    parameters.add(buttonParam);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("messaging_product", "whatsapp");
            data.put("to", phoneNumber);
            data.put("type", "template");

            Map<String, Object> templateData = new HashMap<>();
            templateData.put("name", template.getName());

            Map<String, String> language = new HashMap<>();
            language.put("code", template.getLanguage());
            templateData.put("language", language);
            templateData.put("components", parameters);

            data.put("template", templateData);

            try {
                log.info("=== Broadcast jsonData: {}", objectMapper.writeValueAsString(data));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize template payload", e);
            }

            sendableTemplates.add(data);
        }

        return sendableTemplates;
    }

}