package com.gym.springapp.service;

import com.gym.springapp.dto.*;
import com.gym.springapp.model.Member;

import java.util.List;

public interface PasskeyService {
    PasskeyRegistrationOptionsDTO startRegistration(String authEmail);
    WebAuthnDeviceDTO finishRegistration(String authEmail, PasskeyRegisterVerifyDTO verifyDTO);

    PasskeyAssertionOptionsDTO startAssertion(String authEmailOrNull);
    Member finishAssertion(String authEmailOrNull, PasskeyAssertionVerifyDTO verifyDTO);

    List<WebAuthnDeviceDTO> getMemberDevices(String authEmail, Long memberId, boolean isAdmin);
    void deleteDevice(String authEmail, String credentialId, boolean isAdmin);
}
