package com.minispring.userservice.dto.request;

import static com.minispring.userservice.util.validation.ValidationPattern.NAME_PATTERN;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.minispring.userservice.util.validation.ValidAge;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record UserCreateRequest(
        @NotNull(message = "User ID is required") UUID id,

        @NotBlank(message = "Name cannot be blank")
        @Size(min = 2, max = 100, message = "Name must be between {min} and {max} characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Name contains invalid characters")
        String name,

        @NotBlank
        @Size(min = 2, max = 100, message = "Surname must be between {min} and {max} characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Surname contains invalid characters")
        String surname,

        @NotNull(message = "Birth date is required")
        @ValidAge(min = 6, max = 120, message = "You must be at least {min} and at most {max} years old")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate birthDate,

        @NotBlank(message = "Email cannot be blank") @Email(message = "Invalid email format")
        String email) {
    public UserCreateRequest {
        if (name != null) name = name.trim();
        if (surname != null) surname = surname.trim();
        if (email != null) email = email.trim().toLowerCase();
    }
}
