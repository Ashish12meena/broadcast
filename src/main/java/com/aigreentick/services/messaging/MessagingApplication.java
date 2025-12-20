package com.aigreentick.services.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka 
public class MessagingApplication {
	public static void main(String[] args) {
		SpringApplication.run(MessagingApplication.class, args);
	}
}
 