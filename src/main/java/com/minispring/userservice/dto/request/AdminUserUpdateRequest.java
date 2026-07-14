package com.minispring.userservice.dto.request;

import static com.minispring.userservice.util.validation.ValidationPattern.NAME_PATTERN;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.minispring.userservice.util.validation.ValidAge;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AdminUserUpdateRequest(
        @Size(min = 2, max = 100, message = "Name must be between {min} and {max} characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Name contains invalid characters")
        String name,

        @Size(min = 2, max = 100, message = "Surname must be between {min} and {max} characters long")
        @Pattern(regexp = NAME_PATTERN, message = "Surname contains invalid characters")
        String surname,

        @ValidAge(min = 6, max = 120, message = "You must be at least {min} and at most {max} years old")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate birthDate) {
    public AdminUserUpdateRequest {
        if (name != null) name = name.trim();
        if (surname != null) surname = surname.trim();
    }
}
