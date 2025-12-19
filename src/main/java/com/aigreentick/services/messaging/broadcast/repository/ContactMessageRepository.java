package com.aigreentick.services.messaging.broadcast.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.messaging.broadcast.model.ContactMessage;

public interface ContactMessageRepository extends JpaRepository<ContactMessage,Long> {

    
}