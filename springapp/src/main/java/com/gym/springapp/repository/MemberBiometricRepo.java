package com.gym.springapp.repository;

import com.gym.springapp.model.MemberBiometric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberBiometricRepo extends JpaRepository<MemberBiometric, Long> {
    Optional<MemberBiometric> findByMemberId(Long memberId);
    boolean existsByMemberId(Long memberId);
    void deleteByMemberId(Long memberId);
}
