package com.gym.springapp.dto;

import java.time.Instant;
import java.util.List;

public record WebAuthnDtoBundle() {

    public record RegisterOptionsRequest(
            String deviceName,
            String deviceType,
            String currentPassword
    ) {}

    public record RegisterOptionsResponse(
            String challengeId,
            String publicKeyCredentialCreationOptions,
            int expiresInSeconds,
            boolean reauthVerified
    ) {}

    public record RegisterVerifyRequest(
            String challengeId,
            String credentialJson,
            String deviceName,
            String deviceType,
            List<String> transports,
            boolean consent
    ) {}

    public record RenameDeviceRequest(
            String deviceName
    ) {}

    public record RevokeAllDevicesRequest(
            String currentPassword
    ) {}

    public record QrSessionCreateRequest(
            String gymId,
            String branchId
    ) {}

    public record QrSessionResponse(
            String sessionCode,
            String token,
            String qrUrl,
            String qrCodeDataUrl,
            String gymId,
            String branchId,
            String status,
            Instant createdAt,
            Instant expiresAt,
            Instant serverTime,
            long expiresInSeconds
    ) {}

    public record QrSessionValidateRequest(
            String token,
            Instant clientTimestamp,
            Double latitude,
            Double longitude,
            String wifiSsid
    ) {}

    public record QrSessionValidateResponse(
            boolean valid,
            String sessionCode,
            String gymId,
            String branchId,
            String status,
            Instant expiresAt,
            Instant serverTime,
            long remainingSeconds,
            Long memberId,
            String memberName,
            String planName,
            String membershipStatus,
            boolean hasRegisteredDevices,
            int deviceCount,
            String locationPolicy,
            String riskResult,
            String message
    ) {}

    public record AuthOptionsRequest(
            String qrToken,
            String idempotencyKey
    ) {}

    public record AuthOptionsResponse(
            String challengeId,
            String sessionCode,
            String publicKeyCredentialRequestOptions,
            int expiresInSeconds,
            int allowedDeviceCount
    ) {}

    public record AuthVerifyRequest(
            String challengeId,
            String qrToken,
            String credentialJson,
            String idempotencyKey,
            Double latitude,
            Double longitude,
            String wifiSsid,
            String authenticatorAttachment,
            String verificationHint,
            String fallbackMethod,
            String otpCode
    ) {}

    public record CheckInVerificationResponse(
            String status,
            String message,
            String userVerificationMethod,
            Long memberId,
            String memberName,
            String memberPhotoUrl,
            String planName,
            String membershipStatus,
            String expiryDate,
            String checkInTime,
            Instant checkInTimestamp,
            String checkInMethod,
            String sessionCode,
            String riskResult,
            String idempotencyKey
    ) {}

    public record AdminManualCheckInRequest(
            String sessionCode,
            Long memberId,
            String reason
    ) {}

    public record CheckInWebSocketEvent(
            String sessionCode,
            String status,
            String eventType,
            Long memberId,
            String memberName,
            String memberInitials,
            String memberPhotoUrl,
            String planName,
            String membershipStatus,
            String expiryDate,
            String checkInTime,
            Instant checkInTimestamp,
            String checkInMethod,
            String verificationResult,
            String riskResult,
            String failureReason,
            String branchId,
            Instant timestamp
    ) {}
}
