package com.gym.springapp.repository;

import com.gym.springapp.model.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebAuthnCredentialRepo extends JpaRepository<WebAuthnCredential, Long> {
    List<WebAuthnCredential> findByMemberId(Long memberId);
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
    long countByMemberId(Long memberId);
    void deleteByMemberId(Long memberId);
}
