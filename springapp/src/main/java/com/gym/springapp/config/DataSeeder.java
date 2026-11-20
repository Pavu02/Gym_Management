package com.gym.springapp.config;

import com.gym.springapp.model.Role;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.UserRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepo userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepo.existsByEmail("admin@gym.com")) {
            User admin = new User();
            admin.setName("Admin");
            admin.setEmail("admin@gym.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setPhone("9876543210");
            admin.setRole(Role.ADMIN);
            userRepo.save(admin);
        }
    }
}
