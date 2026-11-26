package com.gym.springapp.controller;

import com.gym.springapp.dto.WebAuthnDtoBundle.CheckInWebSocketEvent;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;

@Controller
public class CheckInWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public CheckInWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/checkin/heartbeat")
    @SendTo("/topic/admin/heartbeat")
    public Map<String, Object> heartbeat() {
        return Map.of("status", "ALIVE", "serverTime", Instant.now().toString());
    }

    public void publishAfterCommit(CheckInWebSocketEvent event) {
        if (event == null || event.sessionCode() == null) {
            return;
        }
        Runnable task = () -> {
            messagingTemplate.convertAndSend("/topic/checkin-sessions/" + event.sessionCode(), event);
            messagingTemplate.convertAndSend("/topic/admin/checkins", event);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    public void publishImmediate(CheckInWebSocketEvent event) {
        if (event == null || event.sessionCode() == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/checkin-sessions/" + event.sessionCode(), event);
        messagingTemplate.convertAndSend("/topic/admin/checkins", event);
    }
}
