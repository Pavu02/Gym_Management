package com.gym.springapp.automation;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.automation.events.MemberWelcomeEvent;
import com.gym.springapp.automation.events.PaymentReceivedEvent;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Payment;
import com.gym.springapp.model.Role;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.PaymentRepo;
import com.gym.springapp.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class AutomationEventListener {

    private static final Logger log = LoggerFactory.getLogger(AutomationEventListener.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final GoogleSheetsService googleSheetsService;
    private final WhatsAppService whatsAppService;
    private final EmailNotificationService emailNotificationService;
    private final MessageTemplates messageTemplates;
    private final AutomationProperties automationProperties;
    private final MemberRepo memberRepo;
    private final PaymentRepo paymentRepo;
    private final UserRepo userRepo;

    public AutomationEventListener(GoogleSheetsService googleSheetsService,
            WhatsAppService whatsAppService,
            EmailNotificationService emailNotificationService,
            MessageTemplates messageTemplates,
            AutomationProperties automationProperties,
            MemberRepo memberRepo,
            PaymentRepo paymentRepo,
            UserRepo userRepo) {
        this.googleSheetsService = googleSheetsService;
        this.whatsAppService = whatsAppService;
        this.emailNotificationService = emailNotificationService;
        this.messageTemplates = messageTemplates;
        this.automationProperties = automationProperties;
        this.memberRepo = memberRepo;
        this.paymentRepo = paymentRepo;
        this.userRepo = userRepo;
    }

    @Async("automationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMemberChanged(MemberChangedEvent event) {
        if (event == null || event.memberId() == null) {
            return;
        }
        try {
            log.info("Processing MemberChangedEvent for member ID {}", event.memberId());
            googleSheetsService.upsertMember(event.memberId());
        } catch (Exception e) {
            log.error("Error processing MemberChangedEvent for member {}: {}", event.memberId(), e.getMessage(), e);
        }
    }

    @Async("automationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePaymentReceived(PaymentReceivedEvent event) {
        if (event == null || event.paymentId() == null) {
            return;
        }
        try {
            log.info("Processing PaymentReceivedEvent for payment ID {}", event.paymentId());
            googleSheetsService.appendPayment(event.paymentId());

            Payment payment = paymentRepo.findById(event.paymentId()).orElse(null);
            if (payment == null) {
                return;
            }

            String payStatus = payment.getStatus();
            boolean isSuccess = "PAID".equalsIgnoreCase(payStatus) || "SUCCESS".equalsIgnoreCase(payStatus);
            if (!isSuccess) {
                log.info("Skipping payment confirmation emails for non-PAID payment #{} (status={})",
                        payment.getId(), payStatus);
                return;
            }

            Member member = payment.getMember();
            if (member == null) {
                return;
            }

            googleSheetsService.upsertMember(member.getId());

            String name = (member.getUser() != null && member.getUser().getName() != null)
                    ? member.getUser().getName()
                    : "Member";
            String email = member.getUser() != null ? member.getUser().getEmail() : null;
            String phone = member.getUser() != null ? member.getUser().getPhone() : null;
            String planName = member.getPlan() != null ? member.getPlan().getName() : "Membership";
            String amount = String.format("%.2f", payment.getAmount());
            String mode = payment.getMode() != null ? payment.getMode() : "Online";
            String expiry = member.getExpiryDate() != null ? member.getExpiryDate().format(DATE_FORMAT) : "N/A";
            String invoiceNo = String.format("INV-%06d", payment.getId() != null ? payment.getId() : 0L);

            String brand = automationProperties.getBrandName();
            if (brand == null || brand.isBlank()) {
                brand = "BeastX Gym";
            }

            String textMessage = messageTemplates.paymentReceivedText(name, amount, mode, expiry);
            String memberHtml = messageTemplates.paymentReceivedHtml(name, amount, mode, expiry);
            String memberSubject = "Payment Confirmation - ₹" + amount + " received [" + brand + "]";

            // 1. Notify Member (WhatsApp + Email)
            if (phone != null && !phone.isBlank()) {
                whatsAppService.sendMessage(phone, textMessage);
            }
            if (email != null && !email.isBlank()) {
                emailNotificationService.sendHtml(email, memberSubject, memberHtml);
            }

            // 2. Notify Admin Recipients (configured ADMIN_EMAILS + active ADMIN accounts)
            Set<String> adminEmails = resolveAdminEmails();
            if (!adminEmails.isEmpty()) {
                String adminSubject = "Payment Received: ₹" + amount + " from " + name + " (" + invoiceNo + ") ["
                        + brand + "]";
                String adminHtml = messageTemplates.adminPaymentAlertHtml(
                        name, email, planName, invoiceNo, amount, mode, expiry);
                for (String adminEmail : adminEmails) {
                    emailNotificationService.sendHtml(adminEmail, adminSubject, adminHtml);
                }
            }
        } catch (Exception e) {
            log.error("Error processing PaymentReceivedEvent for payment {}: {}", event.paymentId(), e.getMessage(), e);
        }
    }

    private Set<String> resolveAdminEmails() {
        Set<String> emails = new LinkedHashSet<>();

        // Configured automation.report.admin-emails
        if (automationProperties.getReport() != null && automationProperties.getReport().getAdminEmails() != null) {
            String[] configured = automationProperties.getReport().getAdminEmails().split(",");
            for (String raw : configured) {
                if (raw != null && raw.trim().contains("@")) {
                    emails.add(raw.trim().toLowerCase());
                }
            }
        }

        // Fallback/supplement: active ADMIN user accounts
        try {
            for (User adminUser : userRepo.findByRoleAndActiveTrueAndDeletedFalse(Role.ADMIN)) {
                if (adminUser.getEmail() != null && adminUser.getEmail().contains("@")) {
                    emails.add(adminUser.getEmail().trim().toLowerCase());
                }
            }
        } catch (Exception ex) {
            log.warn("Could not query admin user emails: {}", ex.getMessage());
        }

        return emails;
    }

    @Async("automationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMemberWelcome(MemberWelcomeEvent event) {
        if (event == null || event.memberId() == null) {
            return;
        }
        try {
            log.info("Processing MemberWelcomeEvent for member ID {}", event.memberId());
            Member member = memberRepo.findById(event.memberId()).orElse(null);
            if (member == null || member.isDeleted() || member.getUser() == null) {
                return;
            }

            String name = member.getUser().getName();
            String email = member.getUser().getEmail();
            String phone = member.getUser().getPhone();
            String plan = member.getPlan() != null ? member.getPlan().getName() : "General";
            String expiry = member.getExpiryDate() != null ? member.getExpiryDate().format(DATE_FORMAT) : "N/A";

            String brand = automationProperties.getBrandName();
            if (brand == null || brand.isBlank()) {
                brand = "BeastX Gym";
            }

            String textMessage = messageTemplates.welcomeText(name, plan, expiry);
            String htmlMessage = messageTemplates.welcomeHtml(name, plan, expiry);
            String subject = "Welcome to " + brand + "! 💪";

            // Send WhatsApp welcome message
            if (phone != null && !phone.isBlank()) {
                whatsAppService.sendMessage(phone, textMessage);
            }

            // Send Email welcome notification
            if (email != null && !email.isBlank()) {
                emailNotificationService.sendHtml(email, subject, htmlMessage);
            }
        } catch (Exception e) {
            log.error("Error processing MemberWelcomeEvent for member {}: {}", event.memberId(), e.getMessage(), e);
        }
    }
}
