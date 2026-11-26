package com.gym.springapp.service;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.automation.events.PaymentReceivedEvent;
import com.gym.springapp.dto.PaymentReportDTO;
import com.gym.springapp.dto.PaymentReportDTO.*;
import com.gym.springapp.dto.PaymentRequestDTO;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Payment;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.PaymentRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepo paymentRepo;
    private final MemberRepo memberRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final ZoneId zoneId;

    public PaymentServiceImpl(
            PaymentRepo paymentRepo,
            MemberRepo memberRepo,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.paymentRepo = paymentRepo;
        this.memberRepo = memberRepo;
        this.eventPublisher = eventPublisher;
        this.zoneId = resolveZone(timezone);
    }

    private ZoneId resolveZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private boolean isSuccessStatus(String status) {
        return "PAID".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
    }

    private void activateMemberOnPayment(Member member) {
        if ("DEACTIVATED".equalsIgnoreCase(member.getStatus()) || !member.isActive()) {
            memberRepo.save(member);
            return;
        }
        LocalDate today = LocalDate.now(zoneId);
        LocalDate baseDate = today;
        if (member.getExpiryDate() != null && member.getExpiryDate().isAfter(today)) {
            baseDate = member.getExpiryDate();
        }
        int durationMonths = (member.getPlan() != null && member.getPlan().getDurationMonths() > 0)
                ? member.getPlan().getDurationMonths()
                : 1;
        member.setExpiryDate(baseDate.plusMonths(durationMonths));
        member.setStatus("ACTIVE");
        if (member.getJoinDate() == null) {
            member.setJoinDate(today);
        }
        member.setOnboardingCompleted(true);
        memberRepo.save(member);
    }

    @Override
    @Transactional
    public Payment addPayment(PaymentRequestDTO request) {
        Member member = memberRepo.findById(request.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + request.memberId()));

        String normalizedStatus = (request.status() != null && !request.status().isBlank())
                ? request.status().trim().toUpperCase()
                : "PAID";
        String normalizedMode = (request.mode() != null && !request.mode().isBlank())
                ? request.mode().trim().toUpperCase()
                : "UPI";

        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(request.amount());
        payment.setPaymentDate(LocalDate.now(zoneId));
        payment.setMode(normalizedMode);
        payment.setStatus(normalizedStatus);

        Payment savedPayment = paymentRepo.save(payment);

        if (isSuccessStatus(normalizedStatus)) {
            activateMemberOnPayment(member);
            eventPublisher.publishEvent(new PaymentReceivedEvent(savedPayment.getId()));
        }
        eventPublisher.publishEvent(new MemberChangedEvent(member.getId()));

        return savedPayment;
    }

    @Override
    @Transactional
    public Payment updatePaymentStatus(Long id, String status) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));

        String previousStatus = payment.getStatus();
        String nextStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : "PAID";
        payment.setStatus(nextStatus);
        Payment savedPayment = paymentRepo.save(payment);

        Member member = savedPayment.getMember();
        if (isSuccessStatus(nextStatus) && !isSuccessStatus(previousStatus)) {
            if (member != null) {
                activateMemberOnPayment(member);
                eventPublisher.publishEvent(new MemberChangedEvent(member.getId()));
            }
            eventPublisher.publishEvent(new PaymentReceivedEvent(savedPayment.getId()));
        }
        return savedPayment;
    }

    @Override
    public List<Payment> getAllPayments() {
        return paymentRepo.findAll();
    }

    @Override
    public List<Payment> getPaymentsByMember(Long memberId) {
        return paymentRepo.findByMemberId(memberId);
    }

    @Override
    public List<Payment> getMyPayments(String email) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        return paymentRepo.findByMemberId(member.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReportDTO getPaymentReport(LocalDate from, LocalDate to, String mode, String status) {
        List<Payment> filtered = filterPayments(from, to, mode, status);

        double totalCollected = filtered.stream()
                .filter(p -> isSuccessStatus(p.getStatus()))
                .mapToDouble(Payment::getAmount)
                .sum();
        double totalPending = filtered.stream()
                .filter(p -> !isSuccessStatus(p.getStatus()))
                .mapToDouble(Payment::getAmount)
                .sum();

        long paidCount = filtered.stream().filter(p -> isSuccessStatus(p.getStatus())).count();
        long pendingCount = filtered.size() - paidCount;
        long totalTransactions = filtered.size();
        double averageTicket = paidCount > 0 ? Math.round((totalCollected / paidCount) * 100.0) / 100.0 : 0.0;
        double collectionRate = totalTransactions > 0
                ? Math.round((paidCount * 1000.0 / totalTransactions)) / 10.0
                : 0.0;

        Map<String, List<Payment>> modeGroups = filtered.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getMode() != null ? p.getMode().toUpperCase() : "OTHER",
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<ModeBreakdown> byMode = modeGroups.entrySet().stream()
                .map(e -> new ModeBreakdown(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().mapToDouble(Payment::getAmount).sum()))
                .sorted(Comparator.comparingDouble(ModeBreakdown::amount).reversed())
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now(zoneId);
        List<MonthlyCollection> byMonth = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate m = today.minusMonths(i);
            int yr = m.getYear();
            int mo = m.getMonthValue();
            List<Payment> monthPayments = filtered.stream()
                    .filter(p -> p.getPaymentDate() != null
                            && p.getPaymentDate().getYear() == yr
                            && p.getPaymentDate().getMonthValue() == mo)
                    .toList();
            double paidAmt = monthPayments.stream()
                    .filter(p -> isSuccessStatus(p.getStatus()))
                    .mapToDouble(Payment::getAmount)
                    .sum();
            double pendingAmt = monthPayments.stream()
                    .filter(p -> !isSuccessStatus(p.getStatus()))
                    .mapToDouble(Payment::getAmount)
                    .sum();
            String label = m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            byMonth.add(new MonthlyCollection(label, paidAmt, pendingAmt, monthPayments.size()));
        }

        List<TransactionRow> transactions = filtered.stream()
                .map(p -> {
                    Member m = p.getMember();
                    String memberName = (m != null && m.getUser() != null) ? m.getUser().getName() : "Member";
                    String memberEmail = (m != null && m.getUser() != null) ? m.getUser().getEmail() : "";
                    String planName = (m != null && m.getPlan() != null) ? m.getPlan().getName() : "Custom / Direct";
                    String invoiceNo = String.format("INV-%06d", p.getId() != null ? p.getId() : 0L);
                    return new TransactionRow(
                            p.getId(),
                            invoiceNo,
                            memberName,
                            memberEmail,
                            planName,
                            p.getAmount(),
                            p.getMode(),
                            p.getStatus(),
                            p.getPaymentDate());
                })
                .collect(Collectors.toList());

        return new PaymentReportDTO(
                totalCollected,
                totalPending,
                paidCount,
                pendingCount,
                totalTransactions,
                averageTicket,
                collectionRate,
                byMode,
                byMonth,
                transactions);
    }

    @Override
    @Transactional(readOnly = true)
    public String exportPaymentReportCsv(LocalDate from, LocalDate to, String mode, String status) {
        PaymentReportDTO report = getPaymentReport(from, to, mode, status);
        StringBuilder sb = new StringBuilder();
        sb.append("Invoice No,Payment ID,Member Name,Member Email,Plan,Amount (INR),Mode,Status,Payment Date\n");
        for (TransactionRow row : report.transactions()) {
            sb.append(csvEscape(row.invoiceNo())).append(',')
                    .append(row.id() != null ? row.id() : "").append(',')
                    .append(csvEscape(row.memberName())).append(',')
                    .append(csvEscape(row.memberEmail())).append(',')
                    .append(csvEscape(row.planName())).append(',')
                    .append(String.format(Locale.US, "%.2f", row.amount())).append(',')
                    .append(csvEscape(row.mode())).append(',')
                    .append(csvEscape(row.status())).append(',')
                    .append(row.paymentDate() != null ? row.paymentDate().toString() : "")
                    .append('\n');
        }
        sb.append('\n')
                .append("SUMMARY,Total Collected (INR),")
                .append(String.format(Locale.US, "%.2f", report.totalCollected()))
                .append(",Pending (INR),").append(String.format(Locale.US, "%.2f", report.totalPending()))
                .append(",Transactions,").append(report.totalTransactions()).append('\n');
        return sb.toString();
    }

    private List<Payment> filterPayments(LocalDate from, LocalDate to, String mode, String status) {
        return paymentRepo.findAll().stream()
                .filter(p -> {
                    if (from != null && (p.getPaymentDate() == null || p.getPaymentDate().isBefore(from))) {
                        return false;
                    }
                    if (to != null && (p.getPaymentDate() == null || p.getPaymentDate().isAfter(to))) {
                        return false;
                    }
                    if (mode != null && !mode.isBlank() && !"ALL".equalsIgnoreCase(mode)) {
                        if (p.getMode() == null || !p.getMode().equalsIgnoreCase(mode)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                        if (p.getStatus() == null || !p.getStatus().equalsIgnoreCase(status)) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Payment::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private String csvEscape(String value) {
        if (value == null)
            return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    @Override
    @Transactional
    public void deletePayment(Long id) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        paymentRepo.delete(payment);
    }
}
