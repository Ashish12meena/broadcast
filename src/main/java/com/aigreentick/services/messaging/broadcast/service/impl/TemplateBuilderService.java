package com.aigreentick.services.messaging.broadcast.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.Template;
import com.aigreentick.services.messaging.broadcast.client.dto.TemplateComponent;
import com.aigreentick.services.messaging.broadcast.dto.BroadcastRequest;
import com.aigreentick.services.messaging.broadcast.dto.build.BuildTemplate;
import com.aigreentick.services.messaging.broadcast.dto.build.Component;
import com.aigreentick.services.messaging.broadcast.dto.build.Language;
import com.aigreentick.services.messaging.broadcast.dto.build.SendableTemplate;
import com.aigreentick.services.messaging.broadcast.model.Broadcast;
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
     * Build sendable templates for all recipients as BuildTemplate objects.
     * FIXED: Now returns proper BuildTemplate objects instead of Map
     */
    public List<BuildTemplate> buildSendableTemplates(
            List<String> validNumbers,
            Template template,
            BroadcastRequest request,
            Long userId,
            Broadcast broadcast) {

        List<BuildTemplate> sendableTemplates = new ArrayList<>();

        String[] variablesArray = request.getVariables() != null
                ? request.getVariables().split(",")
                : new String[0];

        for (String phoneNumber : validNumbers) {
            phoneNumber = phoneNumber.trim();

            List<TemplateComponent> bodyTexts = template.getComponents();
            List<Component> components = new ArrayList<>();

            // Build header component if media exists
            for (TemplateComponent bodyText : bodyTexts) {
                if (bodyText.getFormat() != null) {
                    if (request.getMediaUrl() != null && !request.getMediaUrl().isEmpty()) {
                        Component headerComponent = new Component();
                        headerComponent.setType("header");

                        List<Map<String, Object>> paramList = new ArrayList<>();
                        Map<String, Object> mediaParam = new HashMap<>();
                        mediaParam.put("type", request.getMediaType());

                        Map<String, String> mediaObj = new HashMap<>();
                        mediaObj.put("link", request.getMediaUrl());
                        mediaParam.put(request.getMediaType(), mediaObj);

                        paramList.add(mediaParam);
                        headerComponent.setParameters(paramList);
                        components.add(headerComponent);
                    }
                }
            }

            // Build body and button components if variables exist
            if (request.getVariables() != null) {
                List<Map<String, Object>> vars = new ArrayList<>();
                for (String v : variablesArray) {
                    Map<String, Object> varMap = new HashMap<>();
                    varMap.put("type", "text");
                    varMap.put("text", v);
                    vars.add(varMap);
                }

                // Body component
                Component bodyComponent = new Component();
                bodyComponent.setType("body");
                bodyComponent.setParameters(vars);
                components.add(bodyComponent);

                // Button component for AUTHENTICATION templates
                if ("AUTHENTICATION".equalsIgnoreCase(template.getCategory().trim())) {
                    Component buttonComponent = new Component();
                    buttonComponent.setType("button");
                    buttonComponent.setSubType("url");
                    buttonComponent.setIndex("0");

                    List<Map<String, Object>> buttonParams = new ArrayList<>();
                    Map<String, Object> textParam = new HashMap<>();
                    textParam.put("type", "text");
                    textParam.put("text", vars.isEmpty() ? "" : vars.get(0).get("text"));
                    buttonParams.add(textParam);

                    buttonComponent.setParameters(buttonParams);
                    components.add(buttonComponent);
                }
            }

            // Build SendableTemplate
            SendableTemplate sendableTemplate = new SendableTemplate();
            sendableTemplate.setName(template.getName());
            sendableTemplate.setLanguage(new Language(template.getLanguage()));
            sendableTemplate.setComponents(components);

            // Build final BuildTemplate
            BuildTemplate buildTemplate = BuildTemplate.builder()
                    .messagingProduct("whatsapp")
                    .recipientType("individual")
                    .to(phoneNumber)
                    .type("template")
                    .template(sendableTemplate)
                    .build();

            try {
                log.debug("Built template for {}: {}", 
                    phoneNumber, 
                    objectMapper.writeValueAsString(buildTemplate));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize template payload", e);
            }

            sendableTemplates.add(buildTemplate);
        }

        return sendableTemplates;
    }
}