package com.gym.springapp.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    // Read the values from application.properties
    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String fromNumber;

    // Runs once when the app starts and logs in to Twilio
    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isBlank() && !accountSid.startsWith("PASTE_")
                && authToken != null && !authToken.isBlank() && !authToken.startsWith("PASTE_")) {
            try {
                Twilio.init(accountSid, authToken);
            } catch (Exception e) {
                System.out.println("Twilio init failed: " + e.getMessage());
            }
        }
    }

    // Call this method from anywhere to send an SMS
    public void sendSms(String toNumber, String messageBody) {
        try {
            Message message = Message.creator(
                    new PhoneNumber(toNumber), // receiver, e.g. +91XXXXXXXXXX
                    new PhoneNumber(fromNumber), // your Twilio number
                    messageBody // SMS text
            ).create();
            System.out.println("SMS sent. SID: " + message.getSid());
        } catch (Exception e) {
            System.out.println("SMS failed: " + e.getMessage());
        }
    }
}