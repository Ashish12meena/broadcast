package com.aigreentick.services.messaging.broadcast.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.messaging.broadcast.model.Broadcast;

@Repository
public interface BroadcastRepository extends JpaRepository<Broadcast,Long> {
    
}
