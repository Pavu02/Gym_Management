package com.gym.springapp.config;

import com.gym.springapp.model.User;
import com.gym.springapp.repository.UserRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    public MyUserDetailsService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepo.findByEmailIgnoreCaseAndDeletedFalse(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));
        return new UserPrinciple(user);
    }
}
