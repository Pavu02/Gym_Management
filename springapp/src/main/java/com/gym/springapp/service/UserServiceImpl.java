package com.gym.springapp.service;

import com.gym.springapp.automation.events.MemberChangedEvent;
import com.gym.springapp.config.JwtUtils;
import com.gym.springapp.dto.AuthResponse;
import com.gym.springapp.dto.LoginRequestDTO;
import com.gym.springapp.dto.RegisterRequestDTO;
import com.gym.springapp.exceptions.DuplicateResourceException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Role;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.UserRepo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepo userRepo;
    private final MemberRepo memberRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final ApplicationEventPublisher eventPublisher;

    public UserServiceImpl(UserRepo userRepo,
            MemberRepo memberRepo,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtils jwtUtils,
            ApplicationEventPublisher eventPublisher) {
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AuthResponse register(RegisterRequestDTO request) {
        if (userRepo.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists: " + request.email());
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setRole(Role.MEMBER);

        User savedUser = userRepo.save(user);

        Member member = new Member();
        member.setUser(savedUser);
        member.setStatus("PENDING_PAYMENT");
        member.setOnboardingCompleted(false);
        Member savedMember = memberRepo.save(member);

        eventPublisher.publishEvent(new MemberChangedEvent(savedMember.getId()));

        String token = jwtUtils.generateToken(savedUser);
        return new AuthResponse(token, savedUser.getId(), savedUser.getName(), savedUser.getEmail(),
                savedUser.getRole().name());
    }

    @Override
    public AuthResponse login(LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepo.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.email()));

        String token = jwtUtils.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}
