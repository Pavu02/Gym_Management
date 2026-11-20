package com.gym.springapp.service;

import com.gym.springapp.dto.AuthResponse;
import com.gym.springapp.dto.LoginRequestDTO;
import com.gym.springapp.dto.RegisterRequestDTO;

public interface UserService {
    AuthResponse register(RegisterRequestDTO request);
    AuthResponse login(LoginRequestDTO request);
}
