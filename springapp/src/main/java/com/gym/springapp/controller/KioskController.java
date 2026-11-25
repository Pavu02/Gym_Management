package com.gym.springapp.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.gym.springapp.dto.CheckinResultDTO;
import com.gym.springapp.dto.KioskTokenResponseDTO;
import com.gym.springapp.dto.RecentCheckinDTO;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.model.FaceAttendance;
import com.gym.springapp.model.KioskToken;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.FaceAttendanceRepo;
import com.gym.springapp.repository.KioskTokenRepo;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.UserRepo;
import com.gym.springapp.service.FingerprintService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/kiosk")
@PreAuthorize("hasRole('ADMIN')")
public class KioskController {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    private final KioskTokenRepo kioskTokenRepo;
    private final FaceAttendanceRepo faceAttendanceRepo;
    private final MemberRepo memberRepo;
    private final UserRepo userRepo;
    private final FingerprintService fingerprintService;

    @Value("${checkin.kiosk.token-seconds:60}")
    private int tokenSeconds;

    @Value("${webauthn.origins:http://localhost:8081}")
    private String originsProperty;

    public KioskController(
            KioskTokenRepo kioskTokenRepo,
            FaceAttendanceRepo faceAttendanceRepo,
            MemberRepo memberRepo,
            UserRepo userRepo,
            FingerprintService fingerprintService) {
        this.kioskTokenRepo = kioskTokenRepo;
        this.faceAttendanceRepo = faceAttendanceRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.fingerprintService = fingerprintService;
    }

    private String generateRandomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private String getPrimaryFrontendOrigin() {
        if (originsProperty != null && !originsProperty.trim().isEmpty()) {
            return originsProperty.split(",")[0].trim();
        }
        return "http://localhost:8081";
    }

    @PostMapping("/token")
    public ResponseEntity<KioskTokenResponseDTO> createToken() {
        LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);
        LocalDateTime expiresAt = now.plusSeconds(tokenSeconds);
        String tokenStr = generateRandomToken(32);

        KioskToken kt = new KioskToken();
        kt.setToken(tokenStr);
        kt.setCreatedAt(now);
        kt.setExpiresAt(expiresAt);
        kt.setUsed(false);

        kioskTokenRepo.save(kt);

        String qrUrl = getPrimaryFrontendOrigin() + "/checkin/fingerprint?k=" + tokenStr;
        return ResponseEntity.ok(new KioskTokenResponseDTO(tokenStr, expiresAt, qrUrl));
    }

    @GetMapping(value = "/token/{token}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable String token) {
        KioskToken kt = kioskTokenRepo.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Token not found: " + token));

        String content = getPrimaryFrontendOrigin() + "/checkin/fingerprint?k=" + kt.getToken();

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 400, 400);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(pngData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage(), e);
        }
    }

    @GetMapping("/recent-checkins")
    public ResponseEntity<List<RecentCheckinDTO>> getRecentCheckins(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        List<FaceAttendance> records = faceAttendanceRepo.findByDate(today);

        List<RecentCheckinDTO> result = new ArrayList<>();
        for (FaceAttendance fa : records) {
            LocalDateTime effectiveTime = fa.getCheckOutTime() != null ? fa.getCheckOutTime() : fa.getCheckInTime();
            if (since != null && !effectiveTime.isAfter(since)) {
                continue;
            }

            User user = userRepo.findById(fa.getUserId()).orElse(null);
            Member member = null;
            if (user != null) {
                member = memberRepo.findByUserId(user.getId()).orElse(null);
            }

            String memberName = fa.getUserName();
            String planName = (member != null && member.getPlan() != null) ? member.getPlan().getName() : "Standard";
            LocalDate expiryDate = member != null ? member.getExpiryDate() : null;
            Long daysLeft = (expiryDate != null) ? ChronoUnit.DAYS.between(today, expiryDate) : 0L;
            String status = member != null ? member.getStatus() : "ACTIVE";

            String initials = "";
            if (memberName != null && !memberName.trim().isEmpty()) {
                String[] parts = memberName.trim().split("\\s+");
                initials = parts[0].substring(0, 1).toUpperCase();
                if (parts.length > 1) {
                    initials += parts[parts.length - 1].substring(0, 1).toUpperCase();
                }
            }

            String action = fa.getCheckOutTime() != null ? "CHECK_OUT" : "CHECK_IN";
            String timeStr = effectiveTime.format(TIME_FORMATTER);

            result.add(new RecentCheckinDTO(
                    memberName,
                    initials,
                    planName,
                    expiryDate,
                    status,
                    daysLeft,
                    fa.getMethod(),
                    timeStr,
                    action
            ));
        }

        // Sort descending by time
        return ResponseEntity.ok(result);
    }

    @PostMapping("/simulate/{memberId}")
    public ResponseEntity<CheckinResultDTO> simulateCheckin(@PathVariable Long memberId) {
        CheckinResultDTO result = fingerprintService.simulateCheckin(memberId);
        return ResponseEntity.ok(result);
    }
}
