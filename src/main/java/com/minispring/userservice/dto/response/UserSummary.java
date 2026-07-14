package com.minispring.userservice.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active,
        Instant createdAt,
        Instant updatedAt) {}
