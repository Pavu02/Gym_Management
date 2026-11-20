package com.gym.springapp.controller;

import com.gym.springapp.dto.PaymentRequestDTO;
import com.gym.springapp.model.Payment;
import com.gym.springapp.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Payment> addPayment(@RequestBody PaymentRequestDTO request) {
        Payment created = paymentService.addPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Payment>> getPaymentsByMember(@PathVariable Long memberId) {
        return ResponseEntity.ok(paymentService.getPaymentsByMember(memberId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<List<Payment>> getMyPayments(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(paymentService.getMyPayments(email));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }
}
