package com.aigreentick.services.messaging.broadcast.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.Template;
import com.aigreentick.services.messaging.broadcast.dto.BroadcastRequest;
import com.aigreentick.services.messaging.broadcast.dto.build.BuildTemplate;
import com.aigreentick.services.messaging.broadcast.dto.build.Component;
import com.aigreentick.services.messaging.broadcast.dto.build.Language;
import com.aigreentick.services.messaging.broadcast.dto.build.SendableTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateBuilderService {

    private final ObjectMapper objectMapper;

    public List<BuildTemplate> buildSendableTemplates(
            List<String> validNumbers,
            Template template,
            BroadcastRequest request,
            Long userId,
            Long broadcastId) {

        List<BuildTemplate> sendableTemplates = new ArrayList<>();

        for (String phoneNumber : validNumbers) {
            phoneNumber = phoneNumber.trim();

            List<Component> components = new ArrayList<>();

            // ========== EXACT REPLICA OF SendWhatsappMessageJob LOGIC ==========
            
            // CAROUSEL TEMPLATE
            if (request.getCarouselCards() != null && !request.getCarouselCards().isEmpty()) {
                
                // Build body parameters from variables (lines 140-157 in SendWhatsappMessageJob)
                List<Map<String, Object>> parameters = buildVariableParameters(request.getVariables());

                // Body component with variables
                Component bodyComponent = new Component();
                bodyComponent.setType("body");
                bodyComponent.setParameters(parameters);
                components.add(bodyComponent);

                // Build carousel cards (lines 159-264 in SendWhatsappMessageJob)
                List<Map<String, Object>> cards = new ArrayList<>();
                
                for (int index = 0; index < request.getCarouselCards().size(); index++) {
                    Map<String, Object> cardRequest = request.getCarouselCards().get(index);
                    List<Map<String, Object>> cardComponents = new ArrayList<>();

                    // Card HEADER - image (lines 164-185)
                    if (cardRequest.containsKey("imageUrl") && cardRequest.get("imageUrl") != null) {
                        String imageUrl = (String) cardRequest.get("imageUrl");
                        if (!imageUrl.isEmpty()) {
                            Map<String, Object> headerParam = new HashMap<>();
                            headerParam.put("type", "header");

                            List<Map<String, Object>> headerParams = new ArrayList<>();
                            Map<String, Object> imageParam = new HashMap<>();
                            imageParam.put("type", "image");

                            Map<String, Object> imageObj = new HashMap<>();
                            imageObj.put("link", imageUrl);
                            imageParam.put("image", imageObj);

                            headerParams.add(imageParam);
                            headerParam.put("parameters", headerParams);
                            cardComponents.add(headerParam);
                        }
                    }

                    // Card BODY - variables (lines 187-206)
                    List<Map<String, Object>> cardParams = new ArrayList<>();
                    if (cardRequest.containsKey("variables") && cardRequest.get("variables") != null) {
                        Object variablesObj = cardRequest.get("variables");
                        
                        if (variablesObj instanceof String) {
                            try {
                                Map<Integer, String> vars = objectMapper.readValue((String) variablesObj, Map.class);
                                List<Integer> sortedKeys = new ArrayList<>(vars.keySet());
                                Collections.sort(sortedKeys);
                                
                                for (Integer key : sortedKeys) {
                                    Map<String, Object> varParam = new HashMap<>();
                                    varParam.put("type", "text");
                                    varParam.put("text", vars.get(key));
                                    cardParams.add(varParam);
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse card variables", e);
                            }
                        } else if (variablesObj instanceof Map) {
                            Map<Integer, String> vars = (Map<Integer, String>) variablesObj;
                            List<Integer> sortedKeys = new ArrayList<>(vars.keySet());
                            Collections.sort(sortedKeys);
                            
                            for (Integer key : sortedKeys) {
                                Map<String, Object> varParam = new HashMap<>();
                                varParam.put("type", "text");
                                varParam.put("text", vars.get(key));
                                cardParams.add(varParam);
                            }
                        }
                    }

                    if (!cardParams.isEmpty()) {
                        Map<String, Object> bodyParam = new HashMap<>();
                        bodyParam.put("type", "body");
                        bodyParam.put("parameters", cardParams);
                        cardComponents.add(bodyParam);
                    }

                    // Card BUTTONS (lines 208-255)
                    if (cardRequest.containsKey("buttons") && cardRequest.get("buttons") != null) {
                        List<Map<String, Object>> buttons = (List<Map<String, Object>>) cardRequest.get("buttons");
                        
                        for (int i = 0; i < buttons.size(); i++) {
                            Map<String, Object> btn = buttons.get(i);
                            Map<String, Object> button = new HashMap<>();
                            button.put("type", "button");
                            button.put("sub_type", (String) btn.get("type"));
                            button.put("index", String.valueOf(i));

                            List<Map<String, Object>> buttonParams = new ArrayList<>();

                            if ("quick_reply".equalsIgnoreCase((String) btn.get("type"))) {
                                Map<String, Object> payloadParam = new HashMap<>();
                                payloadParam.put("type", "payload");
                                payloadParam.put("payload", btn.get("text") != null ? btn.get("text") : "");
                                buttonParams.add(payloadParam);
                            } else if ("url".equalsIgnoreCase((String) btn.get("type"))) {
                                String url = btn.get("url") != null ? (String) btn.get("url") : "";

                                if (btn.containsKey("variables") && btn.get("variables") != null) {
                                    Object btnVarsObj = btn.get("variables");
                                    Map<String, String> btnVars = null;
                                    
                                    if (btnVarsObj instanceof String) {
                                        try {
                                            btnVars = objectMapper.readValue((String) btnVarsObj, Map.class);
                                        } catch (Exception e) {
                                            log.error("Failed to parse button variables", e);
                                        }
                                    } else if (btnVarsObj instanceof Map) {
                                        btnVars = (Map<String, String>) btnVarsObj;
                                    }
                                    
                                    if (btnVars != null) {
                                        for (Map.Entry<String, String> entry : btnVars.entrySet()) {
                                            url = url.replace("{{" + entry.getKey() + "}}", entry.getValue());
                                        }
                                    }
                                }

                                Map<String, Object> textParam = new HashMap<>();
                                textParam.put("type", "text");
                                textParam.put("text", url);
                                buttonParams.add(textParam);
                            } else if ("phone_number".equalsIgnoreCase((String) btn.get("type"))) {
                                Map<String, Object> phoneParam = new HashMap<>();
                                phoneParam.put("type", "text");
                                phoneParam.put("text", btn.get("phoneNumber") != null ? btn.get("phoneNumber") : "");
                                buttonParams.add(phoneParam);
                            }

                            button.put("parameters", buttonParams);
                            cardComponents.add(button);
                        }
                    }

                    // Assemble card (lines 257-260)
                    Map<String, Object> cardData = new HashMap<>();
                    cardData.put("card_index", index);
                    cardData.put("components", cardComponents);
                    cards.add(cardData);
                }

                // Carousel component (lines 262-265)
                Component carouselComponent = new Component();
                carouselComponent.setType("carousel");
                carouselComponent.setParameters(cards);
                components.add(carouselComponent);

            } else {
                // NON-CAROUSEL TEMPLATE (lines 267-299)
                
                // HEADER component - media (lines 268-282)
                if (request.getMedia() != null && request.getMedia() && 
                    request.getMediaUrl() != null && !request.getMediaUrl().isEmpty()) {
                    
                    Component headerComponent = new Component();
                    headerComponent.setType("header");

                    List<Map<String, Object>> headerParams = new ArrayList<>();
                    Map<String, Object> mediaParam = new HashMap<>();
                    mediaParam.put("type", request.getMediaType());

                    Map<String, Object> mediaObj = new HashMap<>();
                    mediaObj.put("link", request.getMediaUrl());
                    mediaParam.put(request.getMediaType(), mediaObj);

                    headerParams.add(mediaParam);
                    headerComponent.setParameters(headerParams);
                    components.add(headerComponent);
                }

                // BODY component - variables (lines 284-287)
                List<Map<String, Object>> parameters = buildVariableParameters(request.getVariables());
                
                Component bodyComponent = new Component();
                bodyComponent.setType("body");
                bodyComponent.setParameters(parameters);
                components.add(bodyComponent);

                // BUTTON component - only for AUTHENTICATION (lines 289-299)
                if ("authentication".equalsIgnoreCase(template.getCategory().trim())) {
                    Component buttonComponent = new Component();
                    buttonComponent.setType("button");
                    buttonComponent.setSubType("url");
                    buttonComponent.setIndex("0");

                    List<Map<String, Object>> buttonParams = new ArrayList<>();
                    Map<String, Object> textParam = new HashMap<>();
                    textParam.put("type", "text");
                    textParam.put("text", parameters.isEmpty() ? "" : 
                        (String) parameters.get(0).get("text"));
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

    /**
     * Build variable parameters exactly as in SendWhatsappMessageJob (lines 140-157)
     */
    private List<Map<String, Object>> buildVariableParameters(String variables) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        
        if (variables == null || variables.isEmpty()) {
            return parameters;
        }

        String[] variablesArray = variables.split(",");
        
        for (String value : variablesArray) {
            Map<String, Object> param = new HashMap<>();
            param.put("type", "text");
            param.put("text", value.trim());
            parameters.add(param);
        }

        return parameters;
    }
}