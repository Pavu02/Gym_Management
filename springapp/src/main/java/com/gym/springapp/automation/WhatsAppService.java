package com.gym.springapp.automation;

import com.gym.springapp.automation.entity.AutomationStatus;
import com.gym.springapp.automation.entity.AutomationType;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final Pattern TEN_DIGIT_PATTERN = Pattern.compile("^[0-9]{10}$");

    private final AutomationProperties automationProperties;
    private final AutomationLogService automationLogService;

    private volatile boolean twilioInitialized = false;

    public WhatsAppService(AutomationProperties automationProperties,
                           AutomationLogService automationLogService) {
        this.automationProperties = automationProperties;
        this.automationLogService = automationLogService;
    }

    private synchronized void ensureTwilioInit() {
        if (!twilioInitialized && automationProperties.isWhatsAppConfigured()) {
            try {
                Twilio.init(automationProperties.getTwilioAccountSid(), automationProperties.getTwilioAuthToken());
                twilioInitialized = true;
            } catch (Exception e) {
                log.warn("Twilio initialization note: {}", e.getMessage());
            }
        }
    }

    public String normalizePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        String raw = phone.trim();
        if (raw.toLowerCase().startsWith("whatsapp:")) {
            raw = raw.substring(9).trim();
        }

        // Extract digits only
        String digits = raw.replaceAll("[^0-9]", "");

        // 10-digit Indian mobile number
        if (digits.length() == 10) {
            return "whatsapp:+91" + digits;
        }

        // 11 digits starting with 0 (e.g. 09876543210)
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "whatsapp:+91" + digits.substring(1);
        }

        // 12 digits starting with 91 (e.g. 919876543210)
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "whatsapp:+" + digits;
        }

        return null;
    }

    public boolean sendMessage(String phone, String text) {
        if (!automationProperties.isWhatsAppConfigured()) {
            automationLogService.log(AutomationType.WHATSAPP, phone != null ? phone : "N/A",
                    AutomationStatus.SKIPPED, "Automation disabled or not configured");
            return false;
        }

        String normalized = normalizePhoneNumber(phone);
        if (normalized == null) {
            automationLogService.log(AutomationType.WHATSAPP, phone != null ? phone : "EMPTY",
                    AutomationStatus.SKIPPED, "Invalid or empty phone number");
            return false;
        }

        try {
            ensureTwilioInit();

            String from = automationProperties.getTwilioWhatsappFrom();
            if (from == null || from.trim().isEmpty()) {
                from = "whatsapp:+14155238886";
            } else if (!from.startsWith("whatsapp:")) {
                from = "whatsapp:" + from;
            }

            Message message = Message.creator(
                    new PhoneNumber(normalized),
                    new PhoneNumber(from),
                    text
            ).create();

            String sid = message.getSid();
            log.info("WhatsApp message sent successfully to {}, SID: {}", normalized, sid);
            automationLogService.log(AutomationType.WHATSAPP, normalized,
                    AutomationStatus.SUCCESS, "WhatsApp message sent, SID: " + sid);
            return true;
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", phone, e.getMessage(), e);
            automationLogService.log(AutomationType.WHATSAPP, normalized,
                    AutomationStatus.FAILED, e.getMessage());
            return false;
        }
    }
}
