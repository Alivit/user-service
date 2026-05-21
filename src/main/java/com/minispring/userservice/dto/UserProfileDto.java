package com.minispring.userservice.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@class"
)
public record UserProfileDto(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentCardShortDto> cards
) {
}
