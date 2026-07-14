package com.minispring.userservice.dto.request;

import static com.minispring.userservice.util.validation.ValidationPattern.HOLDER_PATTERN;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import org.hibernate.validator.constraints.CreditCardNumber;

public record PaymentCardUpdateRequest(
        @CreditCardNumber(ignoreNonDigitCharacters = true, message = "Invalid credit card format")
        String number,

        @Size(min = 2, max = 50, message = "Holder must be between {min} and {max} characters long")
        @Pattern(regexp = HOLDER_PATTERN, message = "Invalid holder")
        String holder,

        @JsonFormat(pattern = "MM/yy") @FutureOrPresent(message = "The card has expired")
        YearMonth expirationDate) {
    public PaymentCardUpdateRequest {
        if (holder != null) {
            holder = holder.trim();
        }
        if (number != null) {
            number = number.replaceAll("\\D", "");
        }
    }
}
