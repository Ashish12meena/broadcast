package com.aigreentick.services.messaging.broadcast.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.model.Broadcast;
import com.aigreentick.services.messaging.broadcast.repository.BroadcastRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BroadcastServiceImpl {
    private final BroadcastRepository broadcastRepository;

    public Broadcast save(Broadcast broadcast) {
        return broadcastRepository.save(broadcast);
    }

}
