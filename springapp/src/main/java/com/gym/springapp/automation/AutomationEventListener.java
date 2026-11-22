package com.gym.springapp.automation;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.automation.events.MemberWelcomeEvent;
import com.gym.springapp.automation.events.PaymentReceivedEvent;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Payment;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.PaymentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

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

    public AutomationEventListener(GoogleSheetsService googleSheetsService,
            WhatsAppService whatsAppService,
            EmailNotificationService emailNotificationService,
            MessageTemplates messageTemplates,
            AutomationProperties automationProperties,
            MemberRepo memberRepo,
            PaymentRepo paymentRepo) {
        this.googleSheetsService = googleSheetsService;
        this.whatsAppService = whatsAppService;
        this.emailNotificationService = emailNotificationService;
        this.messageTemplates = messageTemplates;
        this.automationProperties = automationProperties;
        this.memberRepo = memberRepo;
        this.paymentRepo = paymentRepo;
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

            Member member = payment.getMember();
            if (member != null) {
                googleSheetsService.upsertMember(member.getId());

                if (member.getUser() != null) {
                    String name = member.getUser().getName();
                    String email = member.getUser().getEmail();
                    String phone = member.getUser().getPhone();
                    String amount = String.format("%.2f", payment.getAmount());
                    String mode = payment.getMode() != null ? payment.getMode() : "Online";
                    String expiry = member.getExpiryDate() != null ? member.getExpiryDate().format(DATE_FORMAT) : "N/A";

                    String brand = automationProperties.getBrandName();
                    if (brand == null || brand.isBlank()) {
                        brand = "BeastX Gym";
                    }

                    String textMessage = messageTemplates.paymentReceivedText(name, amount, mode, expiry);
                    String htmlMessage = messageTemplates.paymentReceivedHtml(name, amount, mode, expiry);
                    String subject = "Payment Confirmation - ₹" + amount + " received [" + brand + "]";

                    // Send WhatsApp message
                    if (phone != null && !phone.isBlank()) {
                        whatsAppService.sendMessage(phone, textMessage);
                    }

                    // Send Email notification
                    if (email != null && !email.isBlank()) {
                        emailNotificationService.sendHtml(email, subject, htmlMessage);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing PaymentReceivedEvent for payment {}: {}", event.paymentId(), e.getMessage(), e);
        }
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
