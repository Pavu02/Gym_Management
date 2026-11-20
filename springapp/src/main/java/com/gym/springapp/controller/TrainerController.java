package com.gym.springapp.controller;

import com.gym.springapp.dto.MemberResponseDTO;
import com.gym.springapp.dto.TrainerRequestDTO;
import com.gym.springapp.model.Trainer;
import com.gym.springapp.service.MemberService;
import com.gym.springapp.service.TrainerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trainers")
public class TrainerController {

    private final TrainerService trainerService;
    private final MemberService memberService;

    public TrainerController(TrainerService trainerService, MemberService memberService) {
        this.trainerService = trainerService;
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Trainer>> getAllTrainers() {
        return ResponseEntity.ok(trainerService.getAllTrainers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Trainer> getTrainerById(@PathVariable Long id) {
        return ResponseEntity.ok(trainerService.getTrainerById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Trainer> addTrainer(@RequestBody TrainerRequestDTO request) {
        Trainer created = trainerService.addTrainer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Trainer> updateTrainer(@PathVariable Long id, @RequestBody TrainerRequestDTO request) {
        Trainer updated = trainerService.updateTrainer(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTrainer(@PathVariable Long id) {
        trainerService.deleteTrainer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-members")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<MemberResponseDTO>> getMyMembers(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(memberService.getMembersOfTrainer(email));
    }
}
