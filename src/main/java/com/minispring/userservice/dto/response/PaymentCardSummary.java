package com.minispring.userservice.dto.response;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record PaymentCardSummary(
        UUID id,
        String number,
        String holder,
        YearMonth expirationDate,
        Boolean active,
        Instant createdAt,
        Instant updatedAt) {}
