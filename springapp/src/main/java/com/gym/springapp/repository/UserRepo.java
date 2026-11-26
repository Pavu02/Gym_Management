package com.gym.springapp.repository;

import com.gym.springapp.model.Role;
import com.gym.springapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCaseAndDeletedFalse(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);
    long countByRoleAndActiveTrueAndDeletedFalse(Role role);
    List<User> findByRoleAndActiveTrueAndDeletedFalse(Role role);
}
