package com.gym.springapp.config;

import com.gym.springapp.model.Role;
import com.gym.springapp.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrinciple up) {
            return Optional.ofNullable(up.getUser());
        }
        return Optional.empty();
    }

    public static String getCurrentUserEmail() {
        return getCurrentUser().map(User::getEmail).orElse(null);
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().map(User::getId).orElse(null);
    }

    public static Role getCurrentUserRole() {
        return getCurrentUser().map(User::getRole).orElse(null);
    }

    public static boolean isAdmin() {
        return getCurrentUserRole() == Role.ADMIN;
    }

    public static boolean isTrainer() {
        return getCurrentUserRole() == Role.TRAINER;
    }

    public static boolean isMember() {
        return getCurrentUserRole() == Role.MEMBER;
    }
}
