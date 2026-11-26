package com.gym.springapp.dto;

public record MemberDeactivateRequestDTO(
                String reason,
                Long expectedVersion) {
}
