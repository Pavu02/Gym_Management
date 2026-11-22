package com.gym.springapp.automation;

import com.gym.springapp.automation.entity.AutomationStatus;
import com.gym.springapp.automation.entity.AutomationType;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final AutomationProperties automationProperties;
    private final AutomationLogService automationLogService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailNotificationService(AutomationProperties automationProperties,
                                    AutomationLogService automationLogService,
                                    ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.automationProperties = automationProperties;
        this.automationLogService = automationLogService;
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean sendHtml(String to, String subject, String html) {
        return sendHtml(to, subject, html, null, null);
    }

    public boolean sendHtml(String to, String subject, String html,
                            Map<String, byte[]> inlinePngs,
                            Map<String, byte[]> attachments) {

        if (!automationProperties.isEmailConfigured()) {
            automationLogService.log(AutomationType.EMAIL, to != null ? to : "N/A",
                    AutomationStatus.SKIPPED, "Automation disabled or not configured");
            return false;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            automationLogService.log(AutomationType.EMAIL, to != null ? to : "N/A",
                    AutomationStatus.SKIPPED, "JavaMailSender not available");
            return false;
        }

        if (to == null || to.trim().isEmpty() || !to.contains("@")) {
            automationLogService.log(AutomationType.EMAIL, to != null ? to : "EMPTY",
                    AutomationStatus.SKIPPED, "Invalid or empty email address");
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = automationProperties.getMailUsername();
            String brand = automationProperties.getBrandName();
            if (brand == null || brand.isBlank()) {
                brand = "BeastX Gym";
            }

            helper.setFrom(fromEmail, brand);
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(wrapInLayout(html), true);

            // Add inline images
            if (inlinePngs != null && !inlinePngs.isEmpty()) {
                for (Map.Entry<String, byte[]> entry : inlinePngs.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().length > 0) {
                        helper.addInline(entry.getKey(), new ByteArrayResource(entry.getValue()), "image/png");
                    }
                }
            }

            // Add attachments (e.g. PDF report)
            if (attachments != null && !attachments.isEmpty()) {
                for (Map.Entry<String, byte[]> entry : attachments.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().length > 0) {
                        helper.addAttachment(entry.getKey(), new ByteArrayResource(entry.getValue()));
                    }
                }
            }

            mailSender.send(message);
            log.info("Email successfully sent to {} with subject: {}", to, subject);
            automationLogService.log(AutomationType.EMAIL, to.trim(),
                    AutomationStatus.SUCCESS, "Email sent: " + subject);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            automationLogService.log(AutomationType.EMAIL, to != null ? to : "UNKNOWN",
                    AutomationStatus.FAILED, e.getMessage());
            return false;
        }
    }

    private String wrapInLayout(String contentHtml) {
        String brand = automationProperties.getBrandName();
        if (brand == null || brand.isBlank()) {
            brand = "BeastX Gym";
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; background-color: #121212; font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #ffffff;">
                <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #121212; padding: 30px 15px;">
                    <tr>
                        <td align="center">
                            <table width="600" border="0" cellspacing="0" cellpadding="0" style="max-width: 600px; width: 100%%; background-color: #1a1a1a; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.5); border: 1px solid #2a2a2a;">
                                <!-- Header -->
                                <tr>
                                    <td style="background-color: #141414; padding: 25px 30px; border-bottom: 3px solid #ff6b00; text-align: left;">
                                        <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <td>
                                                    <span style="font-size: 24px; font-weight: 900; letter-spacing: 2px; color: #ff6b00; text-transform: uppercase;">%s</span>
                                                </td>
                                                <td align="right" style="color: #888888; font-size: 12px; letter-spacing: 1px; text-transform: uppercase;">
                                                    Official Notification
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Main Content -->
                                <tr>
                                    <td style="padding: 35px 30px; font-size: 15px; line-height: 1.6; color: #e0e0e0;">
                                        %s
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #121212; padding: 20px 30px; text-align: center; border-top: 1px solid #242424; color: #777777; font-size: 12px;">
                                        <p style="margin: 0 0 8px 0; color: #999999;">%s Management System</p>
                                        <p style="margin: 0; color: #555555; font-size: 11px;">This is an automated notification. Please do not reply directly to this email.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(brand, contentHtml, brand);
    }
}
