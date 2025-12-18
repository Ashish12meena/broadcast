package com.aigreentick.services.messaging.broadcast.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.messaging.broadcast.model.ContactMessage;
import com.aigreentick.services.messaging.broadcast.repository.ConatactMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactMessagesServiceImpl {
    private final ConatactMessageRepository contactMessageRepository;

    /**
     * Save contact message with proper error handling
     * Note: contactId should be populated from Contact management system
     * For now, we'll handle missing contactId gracefully
     */
    @Transactional
    public void save(ContactMessage contactMessage) {
        try {
            // Validate required fields
            if (contactMessage.getReportId() == null) {
                log.error("Cannot save ContactMessage without reportId");
                return;
            }
            
            // For now, set a placeholder or make it nullable
            if (contactMessage.getContactId() == null) {
                log.warn("ContactId is null for reportId={}. This should be populated from Contact service.", 
                    contactMessage.getReportId());
                // You might want to fetch contact by mobile number here
                // contactMessage.setContactId(contactService.getOrCreateByMobile(mobile));
            }

            contactMessageRepository.save(contactMessage);
            
            log.debug("Saved ContactMessage. reportId={} contactId={}", 
                contactMessage.getReportId(), contactMessage.getContactId());

        } catch (Exception e) {
            log.error("Failed to save ContactMessage. reportId={}", 
                contactMessage.getReportId(), e);
            // Don't throw - this is a secondary operation that shouldn't fail the broadcast
        }
    }

    /**
     * Create and save ContactMessage from Report data
     * This is the proper way to create ContactMessage entries
     */
    @Transactional
    public void createFromReport(Long reportId, Long contactId, Long chatId) {
        try {
            ContactMessage contactMessage = ContactMessage.builder()
                    .contactId(contactId)
                    .reportId(reportId)
                    .chatId(chatId)
                    .build();

            contactMessageRepository.save(contactMessage);
            
            log.debug("Created ContactMessage from report. reportId={} contactId={}", 
                reportId, contactId);

        } catch (Exception e) {
            log.error("Failed to create ContactMessage from report. reportId={}", reportId, e);
        }
    }
}