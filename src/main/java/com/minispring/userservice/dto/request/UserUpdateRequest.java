package com.minispring.userservice.dto.request;

import static com.minispring.userservice.util.validation.ValidationPattern.NAME_PATTERN;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(min = 2, max = 100, message = "Name must be between {min} and {max} characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Name contains invalid characters")
        String name,

        @Size(min = 2, max = 100, message = "Surname must be between {min} and {max} characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Surname contains invalid characters")
        String surname) {
    public UserUpdateRequest {
        if (name != null) name = name.trim();
        if (surname != null) surname = surname.trim();
    }
}
