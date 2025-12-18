package com.aigreentick.services.messaging.broadcast.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.model.ContactMessage;
import com.aigreentick.services.messaging.broadcast.repository.ConatactMessageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContactMessagesServiceImpl {
    private final ConatactMessageRepository conatactMessageRepository;

    public void save(ContactMessage contactMessage) {
        conatactMessageRepository.save(contactMessage);
    }


}
