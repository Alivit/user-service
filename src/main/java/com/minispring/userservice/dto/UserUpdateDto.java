package com.minispring.userservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.minispring.userservice.util.ValidationPattern.NAME_PATTERN;

public record UserUpdateDto(
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Name is not valid")
        String name,

        @Size(min = 2, max = 100, message = "Surname must be between 2 and 100 characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Surname is not valid")
        String surname
) {
}
