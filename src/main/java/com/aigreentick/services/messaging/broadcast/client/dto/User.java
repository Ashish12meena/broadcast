package com.aigreentick.services.messaging.broadcast.client.dto;


import jakarta.persistence.*;
import lombok.Data;

@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String email;
    
    private Double balance;
    
    private Double debit;
    
    @Column(name = "utilty_msg_charge")
    private Double utiltyMsgCharge;
    
    @Column(name = "market_msg_charge")
    private Double marketMsgCharge;
    
    @Column(name = "auth_msg_charge")
    private Double authMsgCharge;
    
    @Column(name = "created_by")
    private Long createdBy;
}
