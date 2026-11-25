package com.gym.springapp.repository;

import com.gym.springapp.model.MemberPasskey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberPasskeyRepo extends JpaRepository<MemberPasskey, Long> {
    Optional<MemberPasskey> findByCredentialId(String credentialId);
    List<MemberPasskey> findByMemberId(Long memberId);
    List<MemberPasskey> findByUserHandle(String userHandle);
    long countByMemberId(Long memberId);
    void deleteByMemberId(Long memberId);
}
