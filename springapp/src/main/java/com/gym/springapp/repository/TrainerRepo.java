package com.gym.springapp.repository;

import com.gym.springapp.model.Trainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainerRepo extends JpaRepository<Trainer, Long> {
    Optional<Trainer> findByUserEmail(String email);
    Optional<Trainer> findByUserEmailAndDeletedFalse(String email);
    Optional<Trainer> findByIdAndDeletedFalse(Long id);
    List<Trainer> findByDeletedFalse();
    List<Trainer> findByActiveTrueAndDeletedFalse();
}
