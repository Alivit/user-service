package com.minispring.userservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CreditCardNumber;

import java.time.YearMonth;

import static com.minispring.userservice.util.ValidationPattern.HOLDER_PATTERN;

public record PaymentCardUpdateDto(
        @CreditCardNumber
        String number,

        @Size(min = 2, max = 50, message = "Holder must be between 2 and 50 characters long")
        @Pattern(regexp = HOLDER_PATTERN, message = "Invalid holder")
        String holder,


        @JsonFormat(pattern = "MM/yy")
        @FutureOrPresent(message = "The card has expired")
        YearMonth expirationDate,

        Boolean active
) {
}
