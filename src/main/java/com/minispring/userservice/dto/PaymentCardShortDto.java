package com.minispring.userservice.dto;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record PaymentCardShortDto(
        UUID id,
        String number,
        String holder,
        YearMonth expirationDate,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
