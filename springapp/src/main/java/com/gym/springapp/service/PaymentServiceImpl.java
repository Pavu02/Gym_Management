package com.gym.springapp.service;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.automation.events.PaymentReceivedEvent;
import com.gym.springapp.dto.PaymentRequestDTO;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Payment;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.PaymentRepo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepo paymentRepo;
    private final MemberRepo memberRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentServiceImpl(PaymentRepo paymentRepo, MemberRepo memberRepo,
            ApplicationEventPublisher eventPublisher) {
        this.paymentRepo = paymentRepo;
        this.memberRepo = memberRepo;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Payment addPayment(PaymentRequestDTO request) {
        Member member = memberRepo.findById(request.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + request.memberId()));

        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(request.amount());
        payment.setPaymentDate(LocalDate.now());
        payment.setMode(request.mode());
        payment.setStatus(request.status());

        Payment savedPayment = paymentRepo.save(payment);

        if ("PAID".equalsIgnoreCase(request.status())) {
            LocalDate today = LocalDate.now();
            LocalDate baseDate = today;
            if (member.getExpiryDate() != null && member.getExpiryDate().isAfter(today)) {
                baseDate = member.getExpiryDate();
            }
            int durationMonths = (member.getPlan() != null) ? member.getPlan().getDurationMonths() : 1;
            member.setExpiryDate(baseDate.plusMonths(durationMonths));
            member.setStatus("ACTIVE");
            memberRepo.save(member);
        }

        eventPublisher.publishEvent(new PaymentReceivedEvent(savedPayment.getId()));
        if (savedPayment.getMember() != null) {
            eventPublisher.publishEvent(new MemberChangedEvent(savedPayment.getMember().getId()));
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
    @Transactional
    public void deletePayment(Long id) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        paymentRepo.delete(payment);
    }
}
