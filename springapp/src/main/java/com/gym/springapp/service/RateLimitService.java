package com.gym.springapp.service;

import com.gym.springapp.config.CheckInPolicyProperties;
import com.gym.springapp.exceptions.RateLimitExceededException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final CheckInPolicyProperties policy;
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public RateLimitService(CheckInPolicyProperties policy) {
        this.policy = policy;
    }

    public void checkSessionGeneration(Long adminId) {
        enforceLimit("qr-gen:" + adminId, policy.getSessionRateLimitPerMinute(),
                "Too many QR sessions generated. Please wait a moment before creating another.");
    }

    public void checkVerificationRateLimit(Long memberId) {
        enforceReadOnlyLimit("verify-fail:" + memberId, policy.getVerifyFailureRateLimitPerMinute(),
                "Too many failed verification attempts. Please wait a minute or ask an administrator for assistance.");
    }

    public void recordVerificationFailure(Long memberId) {
        recordHit("verify-fail:" + memberId);
    }

    public void clearVerificationFailures(Long memberId) {
        buckets.remove("verify-fail:" + memberId);
    }

    private void enforceLimit(String key, int maxPerMinute, String message) {
        Deque<Instant> deque = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minusSeconds(60);
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxPerMinute) {
                throw new RateLimitExceededException(message);
            }
            deque.addLast(Instant.now());
        }
    }

    private void enforceReadOnlyLimit(String key, int maxPerMinute, String message) {
        Deque<Instant> deque = buckets.get(key);
        if (deque == null) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(60);
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxPerMinute) {
                throw new RateLimitExceededException(message);
            }
        }
    }

    private void recordHit(String key) {
        Deque<Instant> deque = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minusSeconds(60);
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            deque.addLast(Instant.now());
        }
    }
}
