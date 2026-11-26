package com.gym.springapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.springapp.dto.RegisteredDeviceDTO;
import com.gym.springapp.dto.WebAuthnDtoBundle.*;
import com.gym.springapp.exceptions.*;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import com.gym.springapp.service.BiometricCredentialService;
import com.gym.springapp.service.QrCheckInSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@SpringBootTest
class WebAuthnQrCheckInIntegrationTest {

    @Autowired private BiometricCredentialService credentialService;
    @Autowired private QrCheckInSessionService sessionService;
    @Autowired private UserRepo userRepo;
    @Autowired private MemberRepo memberRepo;
    @Autowired private PlanRepo planRepo;
    @Autowired private AttendanceRepo attendanceRepo;
    @Autowired private QrCheckInSessionRepository qrSessionRepo;
    @Autowired private MemberWebAuthnCredentialRepo memberCredentialRepo;
    @Autowired private WebAuthnChallengeRepository challengeRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @SpyBean private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private TrainerRepo trainerRepo;
    private User adminUser;
    private Member activeMember;
    private Member otherMember;
    private Plan goldPlan;

    @BeforeEach
    void setup() {
        challengeRepo.deleteAll();
        attendanceRepo.deleteAll();
        qrSessionRepo.deleteAll();
        memberCredentialRepo.deleteAll();
        reset(messagingTemplate);

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        goldPlan = planRepo.findAll().stream().findFirst().orElseGet(() -> {
            Plan p = new Plan();
            p.setName("Gold Test Plan " + suffix);
            p.setDurationMonths(3);
            p.setPrice(2500.0);
            return planRepo.save(p);
        });

        adminUser = new User();
        adminUser.setName("Admin User");
        adminUser.setEmail("admin." + suffix + "@gym.com");
        adminUser.setPassword(passwordEncoder.encode("admin123"));
        adminUser.setRole(Role.ADMIN);
        adminUser.setActive(true);
        adminUser.setGymId("BEASTX-01");
        adminUser.setBranchId("MAIN");
        adminUser = userRepo.save(adminUser);

        activeMember = createMember("member.one." + suffix + "@gym.com", "Member One", LocalDate.now().plusDays(30), true, "ACTIVE");
        otherMember = createMember("member.two." + suffix + "@gym.com", "Member Two", LocalDate.now().plusDays(30), true, "ACTIVE");
    }

    private Member createMember(String email, String name, LocalDate expiry, boolean active, String status) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("pass1234"));
        u.setPhone("9999999999");
        u.setRole(Role.MEMBER);
        u.setActive(active);
        u.setGymId("BEASTX-01");
        u.setBranchId("MAIN");
        u = userRepo.save(u);

        Member m = new Member();
        m.setUser(u);
        m.setPlan(goldPlan);
        m.setJoinDate(LocalDate.now().minusDays(5));
        m.setExpiryDate(expiry);
        m.setStatus(status);
        m.setOnboardingCompleted(true);
        m.setActive(active);
        m.setDeleted(false);
        m.setGymId("BEASTX-01");
        m.setBranchId("MAIN");
        return memberRepo.save(m);
    }

    private TestAuthenticator enrollAuthenticator(Member member, String deviceName) throws Exception {
        TestAuthenticator auth = TestAuthenticator.generate("localhost", "http://localhost:4200");
        RegisterOptionsResponse opts = credentialService.startRegistration(
                member.getUser().getEmail(),
                new RegisterOptionsRequest(deviceName, "ANDROID_PHONE", null));

        JsonNode creationJson = mapper.readTree(opts.publicKeyCredentialCreationOptions());
        String challenge = creationJson.get("challenge").asText();
        String regPayload = auth.createRegistrationResponseJson(challenge);

        RegisteredDeviceDTO saved = credentialService.finishRegistration(
                member.getUser().getEmail(),
                new RegisterVerifyRequest(opts.challengeId(), regPayload, deviceName, "ANDROID_PHONE", List.of("internal"), true));
        assertNotNull(saved.id());
        assertEquals(deviceName, saved.deviceName());
        return auth;
    }

    @Test
    void testSuccessfulEnrollmentAndQrCheckInWithWebSocketCommitEvent() throws Exception {
        TestAuthenticator auth = enrollAuthenticator(activeMember, "Pixel 9 Primary");
        assertTrue(credentialService.hasActiveDevices(activeMember.getId()));

        QrSessionResponse qr = sessionService.createSession(adminUser.getEmail(), null);
        assertTrue(qr.qrUrl().contains("/check-in/verify?token=" + qr.token()));
        assertFalse(qr.qrUrl().contains(activeMember.getUser().getEmail()));

        QrSessionValidateResponse val = sessionService.validateSessionForMember(
                activeMember.getUser().getEmail(),
                new QrSessionValidateRequest(qr.token(), Instant.now(), null, null, null),
                "127.0.0.1");
        assertTrue(val.valid());
        assertEquals(1, val.deviceCount());

        AuthOptionsResponse authOpts = sessionService.startWebAuthnAssertion(
                activeMember.getUser().getEmail(),
                new AuthOptionsRequest(qr.token(), "idem-ok-1234"));
        JsonNode reqNode = mapper.readTree(authOpts.publicKeyCredentialRequestOptions());
        String challenge = reqNode.path("publicKey").path("challenge").asText();

        String assertionJson = auth.createAssertionResponseJson(challenge, 1L, true);

        reset(messagingTemplate);
        CheckInVerificationResponse result = sessionService.verifyWebAuthnAndCheckIn(
                activeMember.getUser().getEmail(),
                new AuthVerifyRequest(authOpts.challengeId(), qr.token(), assertionJson, "idem-ok-1234", null, null, null, "platform", "BIOMETRIC", null, null),
                "127.0.0.1");

        assertEquals("CHECKED_IN", result.status());
        assertEquals("QR + DEVICE_BIOMETRIC", result.checkInMethod());
        assertEquals("Member One", result.memberName());

        QrCheckInSession updatedSession = qrSessionRepo.findBySessionCode(qr.sessionCode()).orElseThrow();
        assertEquals(QrSessionStatus.COMPLETED, updatedSession.getStatus());
        assertNotNull(updatedSession.getConsumedAt());

        ArgumentCaptor<CheckInWebSocketEvent> wsCaptor = ArgumentCaptor.forClass(CheckInWebSocketEvent.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(startsWith("/topic/checkin-sessions/"), wsCaptor.capture());
        assertEquals("COMPLETED", wsCaptor.getValue().status());
    }

    @Test
    void testInvalidSignatureOriginRpIdAndMissingUvFlagAreRejected() throws Exception {
        TestAuthenticator auth = enrollAuthenticator(activeMember, "Pixel 9");
        QrSessionResponse qr = sessionService.createSession(adminUser.getEmail(), null);

        // 1. Invalid signature
        AuthOptionsResponse opts1 = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "k1"));
        String ch1 = mapper.readTree(opts1.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        String badSig = auth.createAssertionWithTamperedSignature(ch1, 1L);
        assertThrows(VerificationRuleException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts1.challengeId(), qr.token(), badSig, "idem-bad-1", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        // 2. Invalid origin
        TestAuthenticator badOriginAuth = auth.withOrigin("https://evil.attacker.com");
        AuthOptionsResponse opts2 = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "k2"));
        String ch2 = mapper.readTree(opts2.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        String badOriginPayload = badOriginAuth.createAssertionResponseJson(ch2, 2L, true);
        assertThrows(VerificationRuleException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts2.challengeId(), qr.token(), badOriginPayload, "idem-bad-2", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        // 3. Invalid RP ID
        TestAuthenticator badRpAuth = auth.withRpId("evil.com");
        AuthOptionsResponse opts3 = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "k3"));
        String ch3 = mapper.readTree(opts3.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        String badRpPayload = badRpAuth.createAssertionResponseJson(ch3, 3L, true);
        assertThrows(VerificationRuleException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts3.challengeId(), qr.token(), badRpPayload, "idem-bad-3", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        // 4. Missing UV flag
        AuthOptionsResponse opts4 = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "k4"));
        String ch4 = mapper.readTree(opts4.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        String noUvPayload = auth.createAssertionResponseJson(ch4, 4L, false);
        assertThrows(VerificationRuleException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts4.challengeId(), qr.token(), noUvPayload, "idem-bad-4", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));
    }

    @Test
    void testReplayedAndExpiredChallengeAndExpiredAndConsumedQr() throws Exception {
        TestAuthenticator auth = enrollAuthenticator(activeMember, "Pixel 9");
        QrSessionResponse qr = sessionService.createSession(adminUser.getEmail(), null);

        AuthOptionsResponse opts = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "k"));
        String ch = mapper.readTree(opts.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        String payload = auth.createAssertionResponseJson(ch, 1L, true);

        sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                new AuthVerifyRequest(opts.challengeId(), qr.token(), payload, "idem-ok-once", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1");

        // Consumed QR fails on subsequent attempt
        assertThrows(QrSessionConflictException.class, () ->
                sessionService.validateSessionForMember(otherMember.getUser().getEmail(),
                        new QrSessionValidateRequest(qr.token(), Instant.now(), null, null, null), "127.0.0.1"));

        // Replayed challenge fails even against new QR session
        QrSessionResponse qr2 = sessionService.createSession(adminUser.getEmail(), null);
        assertThrows(QrSessionConflictException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts.challengeId(), qr2.token(), payload, "idem-replay-1", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        // Checkout first attendance so business rules permit starting a second check-in verification
        Attendance todayAtt = attendanceRepo.findAll().get(0);
        todayAtt.setCheckOutTime(LocalTime.now());
        attendanceRepo.save(todayAtt);

        // Expired challenge
        AuthOptionsResponse expChallengeOpts = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr2.token(), "k"));
        WebAuthnChallenge storedCh = challengeRepo.findByChallengeId(expChallengeOpts.challengeId()).orElseThrow();
        storedCh.setExpiresAt(Instant.now().minusSeconds(10));
        challengeRepo.save(storedCh);

        String chExp = mapper.readTree(expChallengeOpts.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        String payloadExpCh = auth.createAssertionResponseJson(chExp, 2L, true);
        assertThrows(QrSessionExpiredException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(expChallengeOpts.challengeId(), qr2.token(), payloadExpCh, "idem-exp-ch", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        // Expired QR session
        QrCheckInSession dbQr2 = qrSessionRepo.findBySessionCode(qr2.sessionCode()).orElseThrow();
        dbQr2.setExpiresAt(Instant.now().minusSeconds(30));
        qrSessionRepo.save(dbQr2);
        assertThrows(QrSessionExpiredException.class, () ->
                sessionService.validateSessionForMember(activeMember.getUser().getEmail(),
                        new QrSessionValidateRequest(qr2.token(), Instant.now(), null, null, null), "127.0.0.1"));
    }

    @Test
    void testRevokedCredentialAnotherMemberExpiredMembershipSuspendedAndDuplicateAttendance() throws Exception {
        TestAuthenticator auth = enrollAuthenticator(activeMember, "Phone A");
        List<RegisteredDeviceDTO> devices = credentialService.getMyDevices(activeMember.getUser().getEmail());
        Long devId = devices.get(0).id();

        QrSessionResponse qr = sessionService.createSession(adminUser.getEmail(), null);
        AuthOptionsResponse opts = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "k"));
        String ch = mapper.readTree(opts.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        String assertion = auth.createAssertionResponseJson(ch, 5L, true);

        // Revoke credential before check-in completes -> rejected
        credentialService.revokeMyDevice(activeMember.getUser().getEmail(), devId);
        assertThrows(VerificationRuleException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts.challengeId(), qr.token(), assertion, "idem-revoked", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        // Credential belonging to another member
        enrollAuthenticator(otherMember, "Other Phone");
        assertThrows(ForbiddenOperationException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(otherMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts.challengeId(), qr.token(), assertion, "idem-other", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        // Expired membership
        Member expiredMem = createMember("expired." + UUID.randomUUID().toString().substring(0, 6) + "@gym.com",
                "Expired Mem", LocalDate.now().minusDays(2), true, "ACTIVE");
        assertThrows(VerificationRuleException.class, () ->
                sessionService.validateSessionForMember(expiredMem.getUser().getEmail(),
                        new QrSessionValidateRequest(qr.token(), Instant.now(), null, null, null), "127.0.0.1"));

        // Suspended account
        Member suspendedMem = createMember("suspended." + UUID.randomUUID().toString().substring(0, 6) + "@gym.com",
                "Suspended Mem", LocalDate.now().plusDays(10), false, "SUSPENDED");
        assertThrows(ForbiddenOperationException.class, () ->
                sessionService.validateSessionForMember(suspendedMem.getUser().getEmail(),
                        new QrSessionValidateRequest(qr.token(), Instant.now(), null, null, null), "127.0.0.1"));

        // Duplicate attendance when open check-in exists vs re-entry after checkout
        TestAuthenticator auth2 = enrollAuthenticator(activeMember, "Phone B");
        QrSessionResponse qrA = sessionService.createSession(adminUser.getEmail(), null);
        AuthOptionsResponse optsA = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qrA.token(), "kA"));
        String chA = mapper.readTree(optsA.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();
        sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                new AuthVerifyRequest(optsA.challengeId(), qrA.token(), auth2.createAssertionResponseJson(chA, 1L, true), "idem-dup-1", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1");

        QrSessionResponse qrB = sessionService.createSession(adminUser.getEmail(), null);
        assertThrows(QrSessionConflictException.class, () ->
                sessionService.validateSessionForMember(activeMember.getUser().getEmail(),
                        new QrSessionValidateRequest(qrB.token(), Instant.now(), null, null, null), "127.0.0.1"));
    }

    @Test
    void testConcurrentQrConsumptionAllowsExactlyOneWinnerAndNoRollbackLeak() throws Exception {
        TestAuthenticator auth1 = enrollAuthenticator(activeMember, "Phone 1");
        TestAuthenticator auth2 = enrollAuthenticator(otherMember, "Phone 2");
        QrSessionResponse qr = sessionService.createSession(adminUser.getEmail(), null);

        AuthOptionsResponse o1 = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "c1"));
        AuthOptionsResponse o2 = sessionService.startWebAuthnAssertion(otherMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "c2"));

        String p1 = auth1.createAssertionResponseJson(mapper.readTree(o1.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText(), 1L, true);
        String p2 = auth2.createAssertionResponseJson(mapper.readTree(o2.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText(), 1L, true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);

        Future<Boolean> f1 = pool.submit(() -> {
            gate.await();
            try {
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(o1.challengeId(), qr.token(), p1, "idem-race-1", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1");
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        Future<Boolean> f2 = pool.submit(() -> {
            gate.await();
            try {
                sessionService.verifyWebAuthnAndCheckIn(otherMember.getUser().getEmail(),
                        new AuthVerifyRequest(o2.challengeId(), qr.token(), p2, "idem-race-2", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1");
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        gate.countDown();
        int winners = (f1.get() ? 1 : 0) + (f2.get() ? 1 : 0);
        pool.shutdownNow();

        assertEquals(1, winners, "Exactly one concurrent request may consume a single-use QR session");
        assertEquals(1, attendanceRepo.count());
    }

    @Test
    void testDatabaseRollbackAndNoWebSocketBroadcastOnFailure() throws Exception {
        TestAuthenticator auth = enrollAuthenticator(activeMember, "Pixel");
        QrSessionResponse qr = sessionService.createSession(adminUser.getEmail(), null);
        AuthOptionsResponse opts = sessionService.startWebAuthnAssertion(activeMember.getUser().getEmail(), new AuthOptionsRequest(qr.token(), "rb"));
        String ch = mapper.readTree(opts.publicKeyCredentialRequestOptions()).path("publicKey").path("challenge").asText();

        // Lower signature counter test triggers rollback without creating attendance or COMPLETED broadcast
        MemberWebAuthnCredential stored = memberCredentialRepo.findByMemberIdAndActiveTrue(activeMember.getId()).get(0);
        stored.setSignatureCount(10L);
        memberCredentialRepo.save(stored);

        reset(messagingTemplate);
        String regressedPayload = auth.createAssertionResponseJson(ch, 3L, true);
        assertThrows(VerificationRuleException.class, () ->
                sessionService.verifyWebAuthnAndCheckIn(activeMember.getUser().getEmail(),
                        new AuthVerifyRequest(opts.challengeId(), qr.token(), regressedPayload, "idem-rb", null, null, null, "platform", "BIOMETRIC", null, null), "127.0.0.1"));

        assertEquals(0, attendanceRepo.count());
        QrCheckInSession afterRollback = qrSessionRepo.findBySessionCode(qr.sessionCode()).orElseThrow();
        assertNotEquals(QrSessionStatus.COMPLETED, afterRollback.getStatus());
        verify(messagingTemplate, never()).convertAndSend(startsWith("/topic/checkin-sessions/"),
                argThat((Object arg) -> arg instanceof CheckInWebSocketEvent ev && "COMPLETED".equals(ev.status())));
    }

    /**
     * Minimal real FIDO2 / ECDSA P-256 software authenticator generating spec-compliant WebAuthn
     * attestationObject & assertion signatures verifiable by Yubico java-webauthn-server.
     */
    private static class TestAuthenticator {
        private final KeyPair keyPair;
        private final byte[] credentialId;
        private final String rpId;
        private final String origin;

        private TestAuthenticator(KeyPair keyPair, byte[] credentialId, String rpId, String origin) {
            this.keyPair = keyPair;
            this.credentialId = credentialId;
            this.rpId = rpId;
            this.origin = origin;
        }

        static TestAuthenticator generate(String rpId, String origin) throws Exception {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            byte[] credId = new byte[32];
            new SecureRandom().nextBytes(credId);
            return new TestAuthenticator(kpg.generateKeyPair(), credId, rpId, origin);
        }

        TestAuthenticator withOrigin(String newOrigin) {
            return new TestAuthenticator(keyPair, credentialId, rpId, newOrigin);
        }

        TestAuthenticator withRpId(String newRpId) {
            return new TestAuthenticator(keyPair, credentialId, newRpId, origin);
        }

        String createRegistrationResponseJson(String challengeB64Url) throws Exception {
            String clientDataJson = "{\"type\":\"webauthn.create\",\"challenge\":\"" + challengeB64Url
                    + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}";
            byte[] clientDataBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);

            byte[] coseKey = encodeCoseEc2PublicKey((ECPublicKey) keyPair.getPublic());
            byte[] rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream authData = new ByteArrayOutputStream();
            authData.write(rpIdHash);
            authData.write((byte) 0x45); // UP | UV | AT
            authData.write(ByteBuffer.allocate(4).putInt(0).array());
            authData.write(new byte[16]); // AAGUID
            authData.write(ByteBuffer.allocate(2).putShort((short) credentialId.length).array());
            authData.write(credentialId);
            authData.write(coseKey);

            // Minimal CBOR map {"fmt":"none","attStmt":{},"authData":authData}
            ByteArrayOutputStream cbor = new ByteArrayOutputStream();
            cbor.write((byte) 0xA3);
            writeCborText(cbor, "fmt");
            writeCborText(cbor, "none");
            writeCborText(cbor, "attStmt");
            cbor.write((byte) 0xA0);
            writeCborText(cbor, "authData");
            writeCborBytes(cbor, authData.toByteArray());

            String idB64 = b64Url(credentialId);
            return new ObjectMapper().writeValueAsString(Map.of(
                    "id", idB64,
                    "rawId", idB64,
                    "type", "public-key",
                    "response", Map.of(
                            "clientDataJSON", b64Url(clientDataBytes),
                            "attestationObject", b64Url(cbor.toByteArray()),
                            "transports", List.of("internal")
                    ),
                    "clientExtensionResults", Map.of()
            ));
        }

        String createAssertionResponseJson(String challengeB64Url, long signCount, boolean uv) throws Exception {
            return buildAssertion(challengeB64Url, signCount, uv, false);
        }

        String createAssertionWithTamperedSignature(String challengeB64Url, long signCount) throws Exception {
            return buildAssertion(challengeB64Url, signCount, true, true);
        }

        private String buildAssertion(String challengeB64Url, long signCount, boolean uv, boolean tamperSig) throws Exception {
            String clientDataJson = "{\"type\":\"webauthn.get\",\"challenge\":\"" + challengeB64Url
                    + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}";
            byte[] clientDataBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);
            byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes);

            byte[] rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream authData = new ByteArrayOutputStream();
            authData.write(rpIdHash);
            authData.write((byte) (uv ? 0x05 : 0x01)); // UP + optional UV
            authData.write(ByteBuffer.allocate(4).putInt((int) signCount).array());
            byte[] authDataBytes = authData.toByteArray();

            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(authDataBytes);
            signer.update(clientDataHash);
            byte[] sig = signer.sign();
            if (tamperSig && sig.length > 5) {
                sig[sig.length - 1] ^= 0xFF;
            }

            String idB64 = b64Url(credentialId);
            return new ObjectMapper().writeValueAsString(Map.of(
                    "id", idB64,
                    "rawId", idB64,
                    "type", "public-key",
                    "response", Map.of(
                            "clientDataJSON", b64Url(clientDataBytes),
                            "authenticatorData", b64Url(authDataBytes),
                            "signature", b64Url(sig)
                    ),
                    "clientExtensionResults", Map.of()
            ));
        }

        private static byte[] encodeCoseEc2PublicKey(ECPublicKey key) throws Exception {
            byte[] x = toFixed32(key.getW().getAffineX());
            byte[] y = toFixed32(key.getW().getAffineY());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write((byte) 0xA5); // 5-entry map
            out.write(0x01); out.write(0x02); // kty: EC2
            out.write(0x03); out.write(0x26); // alg: -7 (ES256 -> major type 1 negative: 0x20 + 6 = 0x26)
            out.write(0x20); out.write(0x01); // crv (-1 -> 0x20): P-256 (1)
            out.write(0x21); writeCborBytes(out, x); // x (-2 -> 0x21)
            out.write(0x22); writeCborBytes(out, y); // y (-3 -> 0x22)
            return out.toByteArray();
        }

        private static byte[] toFixed32(BigInteger bi) {
            byte[] raw = bi.toByteArray();
            if (raw.length == 32) return raw;
            byte[] out = new byte[32];
            if (raw.length > 32) {
                System.arraycopy(raw, raw.length - 32, out, 0, 32);
            } else {
                System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
            }
            return out;
        }

        private static void writeCborText(ByteArrayOutputStream out, String text) throws Exception {
            byte[] b = text.getBytes(StandardCharsets.UTF_8);
            out.write((byte) (0x60 | b.length));
            out.write(b);
        }

        private static void writeCborBytes(ByteArrayOutputStream out, byte[] data) throws Exception {
            if (data.length < 24) {
                out.write((byte) (0x40 | data.length));
            } else if (data.length < 256) {
                out.write((byte) 0x58);
                out.write((byte) data.length);
            } else {
                out.write((byte) 0x59);
                out.write((byte) ((data.length >> 8) & 0xFF));
                out.write((byte) (data.length & 0xFF));
            }
            out.write(data);
        }

        private static String b64Url(byte[] bytes) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }
}
