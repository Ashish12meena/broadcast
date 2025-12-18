package com.aigreentick.services.messaging.broadcast.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.messaging.broadcast.model.ContactMessage;

public interface ConatactMessageRepository extends JpaRepository<ContactMessage,Long> {

    
}