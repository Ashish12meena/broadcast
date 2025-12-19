package com.aigreentick.services.messaging.broadcast.client.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.TemplateComponentButton;
import com.aigreentick.services.messaging.broadcast.client.dto.Template;
import com.aigreentick.services.messaging.broadcast.client.dto.TemplateComponent;

@Service
public class TemplateService {

        public Template findByNameAndUserIdNotDeleted(String templateName, Long userId) {
                TemplateComponent bodyComponent = TemplateComponent.builder()
                                .id(5156L)
                                .template_id(2219L)
                                .type("BODY")
                                .text("Welcome to grras, can we connect?")
                                .build();

                TemplateComponentButton templateComponentButton = TemplateComponentButton.builder()
                                .id(1804L)
                                .template_id(2219L)
                                .component_id(5157L)
                                .type("QUICK_REPLY")
                                .text("yes")
                                .build();

                TemplateComponentButton templateComponentButton1 = TemplateComponentButton.builder()
                                .id(1805L)
                                .template_id(2219L)
                                .component_id(5157L)
                                .type("QUICK_REPLY")
                                .text("No")
                                .build();

                TemplateComponent buttonComponent = TemplateComponent.builder()
                                .id(5157L)
                                .template_id(2219L)
                                .type("BUTTONS")
                                .buttons(List.of(templateComponentButton, templateComponentButton1))
                                .build();

                Template template = Template.builder()
                                .id(2219L)
                                .user_id(41L)
                                .name("ashwin_new11")
                                .category("MARKETING")
                                .previous_category("UTILITY")
                                .language("en")
                                .status("APPROVED")
                                .waId("1201441335293844")
                                .components(List.of(bodyComponent, buttonComponent))
                                .build();

                return template;
        }
}
