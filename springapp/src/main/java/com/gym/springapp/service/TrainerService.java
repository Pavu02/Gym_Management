package com.gym.springapp.service;

import com.gym.springapp.dto.TrainerRequestDTO;
import com.gym.springapp.model.Trainer;

import java.util.List;

public interface TrainerService {
    Trainer addTrainer(TrainerRequestDTO request);
    List<Trainer> getAllTrainers();
    Trainer getTrainerById(Long id);
    Trainer updateTrainer(Long id, TrainerRequestDTO request);
    void deleteTrainer(Long id);
}
