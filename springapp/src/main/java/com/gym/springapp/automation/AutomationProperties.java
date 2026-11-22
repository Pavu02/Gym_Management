package com.gym.springapp.automation;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "automation")
public class AutomationProperties {

    private String brandName = "BeastX Gym";

    private Sheets sheets = new Sheets();
    private WhatsApp whatsapp = new WhatsApp();
    private Email email = new Email();
    private Reminder reminder = new Reminder();
    private Report report = new Report();

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.whatsapp-from:}")
    private String twilioWhatsappFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public boolean isConfigured(String val) {
        return val != null && !val.trim().isEmpty() && !val.trim().startsWith("PASTE_");
    }

    public boolean isSheetsConfigured() {
        return sheets != null
                && sheets.isEnabled()
                && isConfigured(sheets.getSpreadsheetId())
                && isConfigured(sheets.getCredentialsFile());
    }

    public boolean isWhatsAppConfigured() {
        return whatsapp != null
                && whatsapp.isEnabled()
                && isConfigured(twilioAccountSid)
                && isConfigured(twilioAuthToken)
                && isConfigured(twilioWhatsappFrom);
    }

    public boolean isEmailConfigured() {
        return email != null
                && email.isEnabled()
                && isConfigured(mailUsername)
                && isConfigured(mailPassword);
    }

    public boolean isReportConfigured() {
        return report != null
                && isConfigured(report.getAdminEmails());
    }

    @Data
    public static class Sheets {
        private boolean enabled = true;
        private String spreadsheetId;
        private String credentialsFile = "google-credentials.json";
        private String resyncCron = "0 0 23 * * *";
    }

    @Data
    public static class WhatsApp {
        private boolean enabled = true;
    }

    @Data
    public static class Email {
        private boolean enabled = true;
    }

    @Data
    public static class Reminder {
        private int daysBefore = 2;
        private String cron = "0 0 9 * * *";
    }

    @Data
    public static class Report {
        private String adminEmails;
        private String adminPhone;
        private String cron = "0 0 8 * * *";
    }
}
