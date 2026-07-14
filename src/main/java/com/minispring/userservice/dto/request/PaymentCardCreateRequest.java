package com.minispring.userservice.dto.request;

import static com.minispring.userservice.util.validation.ValidationPattern.HOLDER_PATTERN;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import org.hibernate.validator.constraints.CreditCardNumber;

public record PaymentCardCreateRequest(
        @NotBlank(message = "Card number is required")
        @CreditCardNumber(ignoreNonDigitCharacters = true, message = "Invalid credit card format")
        String number,

        @NotBlank(message = "Holder is required")
        @Size(min = 2, max = 50, message = "Holder must be between {min} and {max} characters long")
        @Pattern(regexp = HOLDER_PATTERN, message = "Invalid holder")
        String holder,

        @NotNull(message = "Expiration date is required")
        @JsonFormat(pattern = "MM/yy")
        @FutureOrPresent(message = "The card has expired")
        YearMonth expirationDate) {
    public PaymentCardCreateRequest {
        if (holder != null) {
            holder = holder.trim();
        }
        if (number != null) {
            number = number.replaceAll("\\D", "");
        }
    }
}
