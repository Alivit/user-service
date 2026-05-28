package com.minispring.userservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.CARD_LIMIT;
import static com.minispring.userservice.util.ValidationPattern.NAME_PATTERN;

public record UserCreateDto(

        @NotNull
        UUID id,

        @NotBlank
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Name is not valid")
        String name,

        @NotBlank
        @Size(min = 2, max = 100, message = "Surname must be between 2 and 100 characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Surname is not valid")
        String surname,

        @Past(message = "The date of birth must be in the past")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate birthDate,

        @Email
        @NotBlank
        String email
) {
}
