package com.minispring.userservice.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@class"
)
public record PaymentCardProfileDto(
        UUID id,
        UserShortDto user,
        String number,
        String holder,
        YearMonth expirationDate,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
