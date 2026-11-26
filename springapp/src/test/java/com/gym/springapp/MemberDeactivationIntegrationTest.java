package com.gym.springapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.springapp.dto.*;
import com.gym.springapp.dto.WebAuthnDtoBundle.*;
import com.gym.springapp.exceptions.*;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import com.gym.springapp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MemberDeactivationIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private MemberService memberService;
        @Autowired
        private BiometricCredentialService credentialService;
        @Autowired
        private QrCheckInSessionService qrSessionService;
        @Autowired
        private FaceService faceService;
        @Autowired
        private FaceAttendanceService faceAttendanceService;
        @Autowired
        private AttendanceService attendanceService;
        @Autowired
        private DashboardService dashboardService;
        @Autowired
        private UserService userService;

        @Autowired
        private UserRepo userRepo;
        @Autowired
        private MemberRepo memberRepo;
        @Autowired
        private PlanRepo planRepo;
        @Autowired
        private PaymentRepo paymentRepo;
        @Autowired
        private AttendanceRepo attendanceRepo;
        @Autowired
        private FaceProfileRepo faceProfileRepo;
        @Autowired
        private MemberBiometricRepo memberBiometricRepo;
        @Autowired
        private MemberWebAuthnCredentialRepo memberCredentialRepo;
        @Autowired
        private QrCheckInSessionRepository qrSessionRepo;
        @Autowired
        private WebAuthnChallengeRepository challengeRepo;
        @Autowired
        private MemberAuditLogRepo auditLogRepo;
        @Autowired
        private PasswordEncoder passwordEncoder;

        private final ObjectMapper mapper = new ObjectMapper();

        private User adminUser;
        private String adminJwt;
        private Plan plan;

        @BeforeEach
        void setup() {
                auditLogRepo.deleteAll();
                challengeRepo.deleteAll();
                attendanceRepo.deleteAll();
                qrSessionRepo.deleteAll();
                memberCredentialRepo.deleteAll();
                paymentRepo.deleteAll();

                String suffix = UUID.randomUUID().toString().substring(0, 8);
                plan = planRepo.findAll().stream().findFirst().orElseGet(() -> {
                        Plan p = new Plan();
                        p.setName("Platinum " + suffix);
                        p.setDurationMonths(3);
                        p.setPrice(2999.0);
                        return planRepo.save(p);
                });

                adminUser = userRepo.findByEmailIgnoreCase("admin." + suffix + "@gym.com").orElseGet(() -> {
                        User u = new User();
                        u.setName("Admin Controller");
                        u.setEmail("admin.deact." + suffix + "@gym.com");
                        u.setPassword(passwordEncoder.encode("admin123"));
                        u.setRole(Role.ADMIN);
                        u.setActive(true);
                        u.setGymId("BEASTX-01");
                        u.setBranchId("MAIN");
                        return userRepo.save(u);
                });

                AuthResponse adminAuth = userService.login(new LoginRequestDTO(adminUser.getEmail(), "admin123"));
                adminJwt = adminAuth.token();
        }

        private Member createOnboardedMember(String email, String name, LocalDate expiryDate, boolean withPayment) {
                User u = new User();
                u.setName(name);
                u.setEmail(email);
                u.setPassword(passwordEncoder.encode("secret123"));
                u.setPhone("9876543210");
                u.setRole(Role.MEMBER);
                u.setActive(true);
                u.setGymId("BEASTX-01");
                u.setBranchId("MAIN");
                u = userRepo.save(u);

                Member m = new Member();
                m.setUser(u);
                m.setPlan(plan);
                m.setJoinDate(LocalDate.now().minusDays(10));
                m.setExpiryDate(expiryDate);
                m.setStatus(expiryDate.isBefore(LocalDate.now()) ? "EXPIRED" : "ACTIVE");
                m.setOnboardingCompleted(true);
                m.setActive(true);
                m.setFaceEnabled(true);
                m = memberRepo.save(m);

                if (withPayment) {
                        Payment p = new Payment();
                        p.setMember(m);
                        p.setAmount(plan.getPrice());
                        p.setPaymentDate(LocalDate.now().minusDays(10));
                        p.setMode("UPI");
                        p.setStatus("PAID");
                        paymentRepo.save(p);
                }
                return m;
        }

        private List<Double> sampleFaceDescriptor(double seed) {
                List<Double> vec = new ArrayList<>(128);
                for (int i = 0; i < 128; i++) {
                        vec.add(seed + (i * 0.001));
                }
                return vec;
        }

        private TestAuthenticator enrollPhone(Member member, String deviceName) throws Exception {
                TestAuthenticator auth = TestAuthenticator.generate("localhost", "http://localhost:4200");
                RegisterOptionsResponse opts = credentialService.startRegistration(
                                member.getUser().getEmail(),
                                new RegisterOptionsRequest(deviceName, "ANDROID_PHONE", null));
                String challenge = mapper.readTree(opts.publicKeyCredentialCreationOptions()).get("challenge").asText();
                String regJson = auth.createRegistrationResponseJson(challenge);
                credentialService.finishRegistration(
                                member.getUser().getEmail(),
                                new RegisterVerifyRequest(opts.challengeId(), regJson, deviceName, "ANDROID_PHONE",
                                                List.of("internal"), true));
                return auth;
        }

        @Test
        void fullEndToEndDeactivateBlocksAllEntryMethodsWhilePreservingHistoryAndRequiringBiometricReenrollmentOnReactivate()
                        throws Exception {
                String email = "athlete." + UUID.randomUUID().toString().substring(0, 6) + "@gym.com";
                Member member = createOnboardedMember(email, "Rohan Verma", LocalDate.now().plusDays(45), true);

                // 1. Login before deactivation
                String oldMemberJwt = userService.login(new LoginRequestDTO(email, "secret123")).token();

                // 2. Enroll Face ID + 2 WebAuthn devices (primary + backup)
                faceService.enrollFace(member.getUser().getId(), sampleFaceDescriptor(0.25), adminUser.getEmail(),
                                true);
                TestAuthenticator primaryPhone = enrollPhone(member, "Primary Pixel 9");
                enrollPhone(member, "Backup iPhone");
                assertEquals(2, memberCredentialRepo.countByMemberIdAndActiveTrue(member.getId()));
                assertEquals(1, faceService.getAllDescriptors().stream()
                                .filter(d -> d.userId().equals(member.getUser().getId())).count());

                // 3. Check in successfully (simulate open session created before deactivation)
                attendanceService.checkIn(member.getId());
                assertEquals(1, attendanceRepo.count());
                assertTrue(attendanceRepo.existsByMemberIdAndCheckOutTimeIsNull(member.getId()));

                // Generate QR + challenge BEFORE deactivation to test in-flight QR cancellation
                // First checkout existing attendance so challenge can be created
                // pre-deactivation
                attendanceService.checkOut(member.getId());
                QrSessionResponse preDeactQr = qrSessionService.createSession(adminUser.getEmail(), null);
                AuthOptionsResponse preDeactAuthOpts = qrSessionService.startWebAuthnAssertion(
                                email, new AuthOptionsRequest(preDeactQr.token(), "idem-pre-1"));
                String preDeactChallenge = mapper.readTree(preDeactAuthOpts.publicKeyCredentialRequestOptions())
                                .path("publicKey").path("challenge").asText();
                String signedBeforeDeact = primaryPhone.createAssertionResponseJson(preDeactChallenge, 1L, true);

                // Check in again so member ALSO has an active open floor session when
                // deactivated
                attendanceService.checkIn(member.getId());
                assertTrue(attendanceRepo.existsByMemberIdAndCheckOutTimeIsNull(member.getId()));

                long activeBefore = dashboardService.getStats().activeMembers();
                assertTrue(activeBefore >= 1);

                // 4. Admin deactivates member via PATCH /api/admin/members/{id}/deactivate
                mockMvc.perform(patch("/api/admin/members/" + member.getId() + "/deactivate")
                                .header("Authorization", "Bearer " + adminJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Member requested freeze\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.status").value("DEACTIVATED"))
                                .andExpect(jsonPath("$.closedOpenAttendance").value(true))
                                .andExpect(jsonPath("$.message").value("Member deactivated successfully"));

                // Verify DB preservation & revocation
                Member afterDeact = memberRepo.findById(member.getId()).orElseThrow();
                assertEquals("DEACTIVATED", afterDeact.getStatus());
                assertFalse(afterDeact.isActive());
                assertFalse(afterDeact.isFaceEnabled());
                assertNotNull(afterDeact.getDeactivatedAt());
                assertEquals(adminUser.getId(), afterDeact.getDeactivatedBy());
                assertEquals("Member requested freeze", afterDeact.getDeactivationReason());

                // Open attendance auto-closed with ADMIN_DEACTIVATION; historical attendance &
                // payments preserved
                assertFalse(attendanceRepo.existsByMemberIdAndCheckOutTimeIsNull(member.getId()));
                assertEquals(2, attendanceRepo.findByMemberId(member.getId()).size());
                Attendance autoClosed = attendanceRepo.findByMemberId(member.getId()).get(1);
                assertEquals("ADMIN_DEACTIVATION", autoClosed.getCheckoutReason());
                assertEquals(adminUser.getId(), autoClosed.getCheckedOutBy());
                assertEquals(1, paymentRepo.findByMemberId(member.getId()).size());

                // Face templates retained but disabled; WebAuthn credentials revoked with
                // timestamp
                FaceProfile fp = faceProfileRepo.findByUserId(member.getUser().getId()).orElseThrow();
                assertFalse(fp.isEnabled());
                assertEquals("DISABLED", fp.getStatus());
                MemberBiometric mb = memberBiometricRepo.findByMemberId(member.getId()).orElseThrow();
                assertFalse(mb.isEnabled());
                assertEquals("DISABLED", mb.getStatus());
                assertEquals(0, memberCredentialRepo.countByMemberIdAndActiveTrue(member.getId()));
                assertTrue(memberCredentialRepo.findByMemberIdOrderByRegisteredAtDesc(member.getId()).stream()
                                .allMatch(c -> !c.isActive() && c.getRevokedAt() != null));
                assertEquals(0, faceService.getAllDescriptors().stream()
                                .filter(d -> d.userId().equals(member.getUser().getId())).count());

                // Dashboard Active count excludes deactivated member
                assertEquals(activeBefore - 1, dashboardService.getStats().activeMembers());

                // 5. Member login with valid password MUST fail (403, no JWT)
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"))
                                .andExpect(jsonPath("$.message").value(
                                                "Your account has been deactivated. Please contact the gym administrator."))
                                .andExpect(jsonPath("$.token").doesNotExist());

                // 6. Existing valid JWT rejected with 403 Forbidden on sensitive endpoints
                mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + oldMemberJwt))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"));

                mockMvc.perform(put("/api/members/me/profile")
                                .header("Authorization", "Bearer " + oldMemberJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Hacker\"}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"));

                // 7. In-flight signed WebAuthn + QR check-in attempted after deactivation ->
                // DENIED
                mockMvc.perform(post("/api/webauthn/authenticate/verify")
                                .header("Authorization", "Bearer " + oldMemberJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(new AuthVerifyRequest(
                                                preDeactAuthOpts.challengeId(), preDeactQr.token(), signedBeforeDeact,
                                                "idem-post-deact", null, null, null, "platform", "BIOMETRIC", null,
                                                null))))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"));

                // Direct service calls also reject pre-deactivation QR & Face ID & Manual
                // check-in
                assertThrows(MemberDeactivatedException.class, () -> qrSessionService.validateSessionForMember(email,
                                new QrSessionValidateRequest(preDeactQr.token(), Instant.now(), null, null, null),
                                "127.0.0.1"));

                mockMvc.perform(post("/api/attendance/face-checkin")
                                .header("Authorization", "Bearer " + adminJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":" + member.getUser().getId() + "}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"))
                                .andExpect(jsonPath("$.message").value("Access Denied — Member Deactivated"));

                mockMvc.perform(post("/api/attendance/check-in/" + member.getId())
                                .header("Authorization", "Bearer " + adminJwt))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"));

                // Ensure NO new attendance rows were created during blocked attempts
                assertEquals(2, attendanceRepo.findByMemberId(member.getId()).size());

                // Audit logs recorded deactivation + denied attempts
                List<MemberAuditLog> logs = auditLogRepo.findByMemberIdOrderByCreatedAtDesc(member.getId());
                assertTrue(logs.stream().anyMatch(l -> "MEMBER_DEACTIVATED".equals(l.getAction())));
                assertTrue(logs.stream().anyMatch(
                                l -> "CHECKIN_DENIED".equals(l.getAction()) && "DENIED".equals(l.getResult())));

                // 8. Admin clicks Reactivate (valid membership -> ACTIVE)
                mockMvc.perform(patch("/api/admin/members/" + member.getId() + "/reactivate")
                                .header("Authorization", "Bearer " + adminJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.biometricReenrollmentRequired").value(true));

                Member reactivated = memberRepo.findById(member.getId()).orElseThrow();
                assertEquals("ACTIVE", reactivated.getStatus());
                assertTrue(reactivated.isActive());
                assertNull(reactivated.getDeactivatedAt());

                // Security: Old JWT from before deactivation MUST stay invalid!
                mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + oldMemberJwt))
                                .andExpect(status().isUnauthorized());

                // Security: Previously revoked biometrics MUST NOT auto-restore!
                assertFalse(reactivated.isFaceEnabled());
                assertEquals(0, memberCredentialRepo.countByMemberIdAndActiveTrue(member.getId()));
                assertThrows(MemberDeactivatedException.class,
                                () -> faceAttendanceService.faceCheckin(member.getUser().getId()));
                QrSessionResponse freshQr = qrSessionService.createSession(adminUser.getEmail(), null);
                assertThrows(VerificationRuleException.class, () -> qrSessionService.startWebAuthnAssertion(email,
                                new AuthOptionsRequest(freshQr.token(), "idem-no-cred")));

                // 9. Re-login works; re-enrolling Face ID & phone biometric restores full
                // check-in
                String freshJwt = userService.login(new LoginRequestDTO(email, "secret123")).token();
                assertNotNull(freshJwt);

                faceService.enrollFace(member.getUser().getId(), sampleFaceDescriptor(0.45), adminUser.getEmail(),
                                true);
                TestAuthenticator newPhone = enrollPhone(member, "New Re-enrolled Phone");

                AuthOptionsResponse newOpts = qrSessionService.startWebAuthnAssertion(
                                email, new AuthOptionsRequest(freshQr.token(), "idem-reenrolled-ok"));
                String ch2 = mapper.readTree(newOpts.publicKeyCredentialRequestOptions())
                                .path("publicKey").path("challenge").asText();
                CheckInVerificationResponse finalCheckIn = qrSessionService.verifyWebAuthnAndCheckIn(
                                email,
                                new AuthVerifyRequest(newOpts.challengeId(), freshQr.token(),
                                                newPhone.createAssertionResponseJson(ch2, 1L, true),
                                                "idem-reenrolled-ok", null, null, null, "platform", "BIOMETRIC", null,
                                                null),
                                "127.0.0.1");
                assertEquals("CHECKED_IN", finalCheckIn.status());
        }

        @Test
        void testExpiredReactivationEdgeCasesDoubleDeactivationAndConcurrency() throws Exception {
                String emailExp = "expired.deact." + UUID.randomUUID().toString().substring(0, 6) + "@gym.com";
                Member expiredMember = createOnboardedMember(emailExp, "Expired Member", LocalDate.now().minusDays(5),
                                true);

                // Deactivate expired member
                MemberStatusActionResponseDTO deactRes = memberService.deactivateMember(
                                expiredMember.getId(), adminUser.getEmail(), "Expired cleanup", null);
                assertEquals("DEACTIVATED", deactRes.status());

                // Double deactivate is rejected with conflict
                assertThrows(QrSessionConflictException.class, () -> memberService
                                .deactivateMember(expiredMember.getId(), adminUser.getEmail(), "Second click", null));

                // Stale optimistic version rejected with conflict
                assertThrows(QrSessionConflictException.class, () -> memberService
                                .reactivateMember(expiredMember.getId(), adminUser.getEmail(), 999L));

                // Reactivating member with expired dates restores EXPIRED (never blindly
                // ACTIVE!)
                MemberStatusActionResponseDTO reactRes = memberService.reactivateMember(
                                expiredMember.getId(), adminUser.getEmail(), deactRes.version());
                assertEquals("EXPIRED", reactRes.status());

                // Nonexistent member -> 404 ResourceNotFoundException
                assertThrows(ResourceNotFoundException.class,
                                () -> memberService.deactivateMember(999999L, adminUser.getEmail(), "Missing", null));

                // Protected Admin member cannot be deactivated
                Member adminMember = new Member();
                adminMember.setUser(adminUser);
                adminMember.setStatus("ACTIVE");
                adminMember.setOnboardingCompleted(true);
                adminMember = memberRepo.save(adminMember);
                Long adminMemberId = adminMember.getId();
                assertThrows(ForbiddenOperationException.class, () -> memberService.deactivateMember(adminMemberId,
                                adminUser.getEmail(), "Attack", null));

                // Concurrent deactivation race: exactly one admin wins
                Member raceMember = createOnboardedMember(
                                "race." + UUID.randomUUID().toString().substring(0, 6) + "@gym.com",
                                "Concurrent Athlete", LocalDate.now().plusDays(15), false);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                CountDownLatch gate = new CountDownLatch(1);
                Future<Boolean> t1 = pool.submit(() -> {
                        gate.await();
                        try {
                                memberService.deactivateMember(raceMember.getId(), adminUser.getEmail(), "Admin A",
                                                null);
                                return true;
                        } catch (Exception e) {
                                return false;
                        }
                });
                Future<Boolean> t2 = pool.submit(() -> {
                        gate.await();
                        try {
                                memberService.deactivateMember(raceMember.getId(), adminUser.getEmail(), "Admin B",
                                                null);
                                return true;
                        } catch (Exception e) {
                                return false;
                        }
                });
                gate.countDown();
                int wins = (t1.get() ? 1 : 0) + (t2.get() ? 1 : 0);
                pool.shutdownNow();
                assertEquals(1, wins);
                assertEquals("DEACTIVATED", memberRepo.findById(raceMember.getId()).orElseThrow().getStatus());
        }

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

                String createRegistrationResponseJson(String challengeB64Url) throws Exception {
                        String clientDataJson = "{\"type\":\"webauthn.create\",\"challenge\":\"" + challengeB64Url
                                        + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}";
                        byte[] clientDataBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);

                        byte[] coseKey = encodeCoseEc2PublicKey((ECPublicKey) keyPair.getPublic());
                        byte[] rpIdHash = MessageDigest.getInstance("SHA-256")
                                        .digest(rpId.getBytes(StandardCharsets.UTF_8));
                        ByteArrayOutputStream authData = new ByteArrayOutputStream();
                        authData.write(rpIdHash);
                        authData.write((byte) 0x45);
                        authData.write(ByteBuffer.allocate(4).putInt(0).array());
                        authData.write(new byte[16]);
                        authData.write(ByteBuffer.allocate(2).putShort((short) credentialId.length).array());
                        authData.write(credentialId);
                        authData.write(coseKey);

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
                                                        "transports", List.of("internal")),
                                        "clientExtensionResults", Map.of()));
                }

                String createAssertionResponseJson(String challengeB64Url, long signCount, boolean uv)
                                throws Exception {
                        String clientDataJson = "{\"type\":\"webauthn.get\",\"challenge\":\"" + challengeB64Url
                                        + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}";
                        byte[] clientDataBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);
                        byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes);

                        byte[] rpIdHash = MessageDigest.getInstance("SHA-256")
                                        .digest(rpId.getBytes(StandardCharsets.UTF_8));
                        ByteArrayOutputStream authData = new ByteArrayOutputStream();
                        authData.write(rpIdHash);
                        authData.write((byte) (uv ? 0x05 : 0x01));
                        authData.write(ByteBuffer.allocate(4).putInt((int) signCount).array());
                        byte[] authDataBytes = authData.toByteArray();

                        Signature signer = Signature.getInstance("SHA256withECDSA");
                        signer.initSign(keyPair.getPrivate());
                        signer.update(authDataBytes);
                        signer.update(clientDataHash);
                        byte[] sig = signer.sign();

                        String idB64 = b64Url(credentialId);
                        return new ObjectMapper().writeValueAsString(Map.of(
                                        "id", idB64,
                                        "rawId", idB64,
                                        "type", "public-key",
                                        "response", Map.of(
                                                        "clientDataJSON", b64Url(clientDataBytes),
                                                        "authenticatorData", b64Url(authDataBytes),
                                                        "signature", b64Url(sig)),
                                        "clientExtensionResults", Map.of()));
                }

                private static byte[] encodeCoseEc2PublicKey(ECPublicKey key) throws Exception {
                        byte[] x = toFixed32(key.getW().getAffineX());
                        byte[] y = toFixed32(key.getW().getAffineY());
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        out.write((byte) 0xA5);
                        out.write(0x01);
                        out.write(0x02);
                        out.write(0x03);
                        out.write(0x26);
                        out.write(0x20);
                        out.write(0x01);
                        out.write(0x21);
                        writeCborBytes(out, x);
                        out.write(0x22);
                        writeCborBytes(out, y);
                        return out.toByteArray();
                }

                private static byte[] toFixed32(BigInteger bi) {
                        byte[] raw = bi.toByteArray();
                        if (raw.length == 32)
                                return raw;
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
