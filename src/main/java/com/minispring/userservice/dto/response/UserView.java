package com.minispring.userservice.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserView(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active,
        Boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentCardSummary> cards) {}
