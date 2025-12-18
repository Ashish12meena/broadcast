package com.aigreentick.services.messaging.broadcast.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.messaging.broadcast.model.BroadcastMedia;

public interface BroadcastMediaRepository extends JpaRepository<BroadcastMedia, Long> {
    
}