package com.gym.springapp.repository;

import com.gym.springapp.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepo extends JpaRepository<Member, Long> {
    Optional<Member> findByUserEmail(String email);
    Optional<Member> findByUserId(Long userId);
    Optional<Member> findByUserEmailAndDeletedFalse(String email);
    Optional<Member> findByIdAndDeletedFalse(Long id);
    List<Member> findByTrainerId(Long id);
    List<Member> findByTrainerIdAndDeletedFalse(Long id);
    List<Member> findByPlanId(Long id);
    long countByStatus(String status);
    long countByTrainerIdAndDeletedFalse(Long id);
    List<Member> findByDeletedFalse();
}
