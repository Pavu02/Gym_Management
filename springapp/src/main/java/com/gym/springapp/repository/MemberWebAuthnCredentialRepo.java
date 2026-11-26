package com.gym.springapp.repository;

import com.gym.springapp.model.MemberWebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberWebAuthnCredentialRepo extends JpaRepository<MemberWebAuthnCredential, Long> {

    Optional<MemberWebAuthnCredential> findByCredentialId(String credentialId);

    Optional<MemberWebAuthnCredential> findByCredentialIdAndActiveTrue(String credentialId);

    List<MemberWebAuthnCredential> findByMemberIdAndActiveTrue(Long memberId);

    List<MemberWebAuthnCredential> findByMemberIdOrderByRegisteredAtDesc(Long memberId);

    long countByMemberIdAndActiveTrue(Long memberId);

    Optional<MemberWebAuthnCredential> findByIdAndMemberId(Long id, Long memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MemberWebAuthnCredential mwc WHERE mwc.member.id = :memberId")
    void deleteDirectByMemberId(@Param("memberId") Long memberId);
}
