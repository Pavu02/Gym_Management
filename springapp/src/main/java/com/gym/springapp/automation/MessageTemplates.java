package com.gym.springapp.automation;

import org.springframework.stereotype.Component;

@Component
public class MessageTemplates {

    private final AutomationProperties automationProperties;

    public MessageTemplates(AutomationProperties automationProperties) {
        this.automationProperties = automationProperties;
    }

    private String getBrand() {
        String brand = automationProperties.getBrandName();
        return (brand != null && !brand.isBlank()) ? brand : "BeastX Gym";
    }

    // 1. WELCOME
    public String welcomeText(String name, String plan, String expiry) {
        String safePlan = (plan != null && !plan.isBlank()) ? plan : "Membership";
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "N/A";
        return String.format("Hi %s, welcome to %s! 💪 Your %s membership is active till %s.",
                name, getBrand(), safePlan, safeExpiry);
    }

    public String welcomeHtml(String name, String plan, String expiry) {
        String safePlan = (plan != null && !plan.isBlank()) ? plan : "Membership";
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "N/A";
        return """
            <h2 style="color: #ff6b00; margin-top: 0;">Welcome to the Beast Pack! 💪</h2>
            <p style="font-size: 16px; color: #ffffff;">Hi <strong>%s</strong>,</p>
            <p style="font-size: 15px; color: #d0d0d0; line-height: 1.6;">
                Welcome to <strong>%s</strong>! Your fitness journey starts today. Your <strong>%s</strong> membership has been successfully activated.
            </p>
            <div style="background-color: #222222; border-left: 4px solid #ff6b00; padding: 15px 20px; border-radius: 6px; margin: 25px 0;">
                <p style="margin: 0; color: #aaaaaa; font-size: 13px; text-transform: uppercase; letter-spacing: 1px;">Plan Details</p>
                <p style="margin: 6px 0 0 0; color: #ffffff; font-size: 18px; font-weight: bold;">%s</p>
                <p style="margin: 4px 0 0 0; color: #ff6b00; font-size: 14px;">Valid till: %s</p>
            </div>
            <p style="font-size: 14px; color: #aaaaaa; line-height: 1.5;">
                Get ready to push your limits. Open the app to view your workouts, manage your trainer, and mark your attendance!
            </p>
            """.formatted(name, getBrand(), safePlan, safePlan, safeExpiry);
    }

    // 2. EXPIRY_2_DAYS
    public String expiry2DaysText(String name, String plan, String expiry) {
        String safePlan = (plan != null && !plan.isBlank()) ? plan : "Gym";
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "in 2 days";
        return String.format("Hi %s, your %s %s membership expires in 2 days (%s). Renew now to keep training without a break! 🏋️",
                name, getBrand(), safePlan, safeExpiry);
    }

    public String expiry2DaysHtml(String name, String plan, String expiry) {
        String safePlan = (plan != null && !plan.isBlank()) ? plan : "Gym";
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "in 2 days";
        return """
            <h2 style="color: #ff6b00; margin-top: 0;">Membership Expiring Soon! 🏋️</h2>
            <p style="font-size: 16px; color: #ffffff;">Hi <strong>%s</strong>,</p>
            <p style="font-size: 15px; color: #d0d0d0; line-height: 1.6;">
                Your <strong>%s %s</strong> membership is expiring in <strong>2 days (%s)</strong>.
            </p>
            <div style="background-color: #2a2015; border-left: 4px solid #ff6b00; padding: 15px 20px; border-radius: 6px; margin: 25px 0;">
                <p style="margin: 0; color: #ff9944; font-size: 13px; text-transform: uppercase; letter-spacing: 1px;">Action Required</p>
                <p style="margin: 6px 0 0 0; color: #ffffff; font-size: 16px; font-weight: bold;">Keep training without interruption</p>
                <p style="margin: 4px 0 0 0; color: #cccccc; font-size: 14px;">Renew your membership through the app or visit the reception desk today.</p>
            </div>
            """.formatted(name, getBrand(), safePlan, safeExpiry);
    }

    // 3. EXPIRED
    public String expiredText(String name, String expiry) {
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "recently";
        return String.format("Hi %s, your %s membership expired on %s. Visit the gym or renew from the app.",
                name, getBrand(), safeExpiry);
    }

    public String expiredHtml(String name, String expiry) {
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "recently";
        return """
            <h2 style="color: #e53935; margin-top: 0;">Membership Expired</h2>
            <p style="font-size: 16px; color: #ffffff;">Hi <strong>%s</strong>,</p>
            <p style="font-size: 15px; color: #d0d0d0; line-height: 1.6;">
                Your <strong>%s</strong> membership expired on <strong>%s</strong>.
            </p>
            <div style="background-color: #261616; border-left: 4px solid #e53935; padding: 15px 20px; border-radius: 6px; margin: 25px 0;">
                <p style="margin: 0; color: #ff6b6b; font-size: 13px; text-transform: uppercase; letter-spacing: 1px;">Status: Inactive</p>
                <p style="margin: 6px 0 0 0; color: #ffffff; font-size: 15px;">We miss you at the gym! Renew now to regain access to all facilities and classes.</p>
            </div>
            """.formatted(name, getBrand(), safeExpiry);
    }

    // 4. PAYMENT_RECEIVED
    public String paymentReceivedText(String name, String amount, String mode, String expiry) {
        String safeMode = (mode != null && !mode.isBlank()) ? mode : "Online";
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "N/A";
        return String.format("Hi %s, we received ₹%s via %s. Membership valid till %s. Thank you! 🙏",
                name, amount, safeMode, safeExpiry);
    }

    public String paymentReceivedHtml(String name, String amount, String mode, String expiry) {
        String safeMode = (mode != null && !mode.isBlank()) ? mode : "Online";
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "N/A";
        return """
            <h2 style="color: #ff6b00; margin-top: 0;">Payment Confirmation 🙏</h2>
            <p style="font-size: 16px; color: #ffffff;">Hi <strong>%s</strong>,</p>
            <p style="font-size: 15px; color: #d0d0d0; line-height: 1.6;">
                Thank you for your payment! We have successfully received your membership payment.
            </p>
            <div style="background-color: #222222; border-left: 4px solid #4caf50; padding: 15px 20px; border-radius: 6px; margin: 25px 0;">
                <table style="width: 100%%; border-collapse: collapse; color: #ffffff;">
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 14px;">Amount Received:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 18px; font-weight: bold; color: #4caf50;">₹%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 14px;">Payment Mode:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 15px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 14px;">Membership Valid Till:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 15px; color: #ff6b00; font-weight: bold;">%s</td>
                    </tr>
                </table>
            </div>
            """.formatted(name, amount, safeMode, safeExpiry);
    }

    // 5. ADMIN_PAYMENT_ALERT
    public String adminPaymentAlertHtml(
            String memberName,
            String memberEmail,
            String planName,
            String invoiceNo,
            String amount,
            String mode,
            String expiry) {
        String safeName = (memberName != null && !memberName.isBlank()) ? memberName : "Member";
        String safeEmail = (memberEmail != null && !memberEmail.isBlank()) ? memberEmail : "N/A";
        String safePlan = (planName != null && !planName.isBlank()) ? planName : "Membership";
        String safeInvoice = (invoiceNo != null && !invoiceNo.isBlank()) ? invoiceNo : "INV-NEW";
        String safeMode = (mode != null && !mode.isBlank()) ? mode : "Online";
        String safeExpiry = (expiry != null && !expiry.isBlank()) ? expiry : "N/A";

        return """
            <h2 style="color: #ff6b00; margin-top: 0;">💰 New Payment Received</h2>
            <p style="font-size: 15px; color: #d0d0d0; line-height: 1.6;">
                A membership payment has been verified and recorded in <strong>%s</strong>.
            </p>
            <div style="background-color: #222222; border-left: 4px solid #ff6b00; padding: 16px 20px; border-radius: 6px; margin: 22px 0;">
                <table style="width: 100%%; border-collapse: collapse; color: #ffffff;">
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 13px;">Invoice No:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 14px; font-weight: bold; color: #ffffff;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 13px;">Member:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 14px; font-weight: bold;">%s (%s)</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 13px;">Plan:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 14px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 13px;">Amount Received:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 18px; font-weight: bold; color: #4caf50;">₹%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 13px;">Payment Mode:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 14px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 0; color: #aaaaaa; font-size: 13px;">Valid Till:</td>
                        <td style="padding: 6px 0; text-align: right; font-size: 14px; color: #ff6b00; font-weight: bold;">%s</td>
                    </tr>
                </table>
            </div>
            <p style="font-size: 13px; color: #999999; margin: 0;">
                Member confirmation email &amp; Google Sheets sync have been triggered automatically.
            </p>
            """.formatted(getBrand(), safeInvoice, safeName, safeEmail, safePlan, amount, safeMode, safeExpiry);
    }
}
