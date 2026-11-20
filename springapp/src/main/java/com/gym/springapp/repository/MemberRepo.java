package com.gym.springapp.repository;

import com.gym.springapp.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepo extends JpaRepository<Member, Long> {
    Optional<Member> findByUserEmail(String email);
    List<Member> findByTrainerId(Long id);
    List<Member> findByPlanId(Long id);
    long countByStatus(String status);
}
