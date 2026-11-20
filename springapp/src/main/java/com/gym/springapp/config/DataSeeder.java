package com.gym.springapp.config;

import com.gym.springapp.model.Plan;
import com.gym.springapp.model.Role;
import com.gym.springapp.model.Trainer;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.PlanRepo;
import com.gym.springapp.repository.TrainerRepo;
import com.gym.springapp.repository.UserRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepo userRepo;
    private final PlanRepo planRepo;
    private final TrainerRepo trainerRepo;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepo userRepo,
                      PlanRepo planRepo,
                      TrainerRepo trainerRepo,
                      PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.planRepo = planRepo;
        this.trainerRepo = trainerRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Seed Admin
        if (!userRepo.existsByEmail("admin@gym.com")) {
            User admin = new User();
            admin.setName("Admin");
            admin.setEmail("admin@gym.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setPhone("9876543210");
            admin.setRole(Role.ADMIN);
            userRepo.save(admin);
        }

        // Seed Plans if table is empty or missing standard plans
        if (!planRepo.existsByName("Basic")) {
            Plan basic = new Plan();
            basic.setName("Basic");
            basic.setDurationMonths(1);
            basic.setPrice(999.0);
            basic.setDescription("Essential gym access with locker room");
            basic.setFeatures("Gym Access,Locker Room,Free WiFi");
            basic.setPopular(false);
            planRepo.save(basic);
        }

        if (!planRepo.existsByName("Premium")) {
            Plan premium = new Plan();
            premium.setName("Premium");
            premium.setDurationMonths(3);
            premium.setPrice(2499.0);
            premium.setDescription("Full gym access plus classes and diet advice");
            premium.setFeatures("Gym Access,Locker Room,Group Classes,Diet Consultation");
            premium.setPopular(true);
            planRepo.save(premium);
        }

        if (!planRepo.existsByName("Elite")) {
            Plan elite = new Plan();
            elite.setName("Elite");
            elite.setDurationMonths(12);
            elite.setPrice(8999.0);
            elite.setDescription("All-inclusive VIP experience with steam and perks");
            elite.setFeatures("Gym Access,Locker Room,Group Classes,Personalized Diet Plan,Steam & Sauna,Free Merchandise");
            elite.setPopular(false);
            planRepo.save(elite);
        }

        // Seed Trainers
        if (!userRepo.existsByEmail("rahul@gym.com")) {
            User u1 = new User();
            u1.setName("Rahul Sharma");
            u1.setEmail("rahul@gym.com");
            u1.setPassword(passwordEncoder.encode("trainer123"));
            u1.setPhone("9876543201");
            u1.setRole(Role.TRAINER);
            User savedU1 = userRepo.save(u1);

            Trainer t1 = new Trainer();
            t1.setUser(savedU1);
            t1.setSpecialization("Strength & Muscle Gain");
            t1.setExperienceYears(5);
            t1.setBio("Certified fitness trainer specializing in heavy lifting, bodybuilding and muscle building programs.");
            t1.setRating(4.8);
            t1.setMonthlyFee(1500.0);
            trainerRepo.save(t1);
        }

        if (!userRepo.existsByEmail("priya@gym.com")) {
            User u2 = new User();
            u2.setName("Priya Patil");
            u2.setEmail("priya@gym.com");
            u2.setPassword(passwordEncoder.encode("trainer123"));
            u2.setPhone("9876543202");
            u2.setRole(Role.TRAINER);
            User savedU2 = userRepo.save(u2);

            Trainer t2 = new Trainer();
            t2.setUser(savedU2);
            t2.setSpecialization("Weight Loss & Yoga");
            t2.setExperienceYears(4);
            t2.setBio("Passionate trainer focusing on functional fitness, yoga flow, core strength, and sustainable weight loss.");
            t2.setRating(4.9);
            t2.setMonthlyFee(1200.0);
            trainerRepo.save(t2);
        }
    }
}
