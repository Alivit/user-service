package com.minispring.userservice.util.validation;

public final class ValidationPattern {
    public static final String NAME_PATTERN =
            "^[a-zA-Zа-яА-ЯёЁ]+(?:[-'][a-zA-Zа-яА-ЯёЁ]+)*(?:\\s+[a-zA-Zа-яА-ЯёЁ]+(?:[-'][a-zA-Zа-яА-ЯёЁ]+)*){0,2}$";
    public static final String HOLDER_PATTERN = "^((?:[a-zA-Z]+ ?){1,2})$";

    ValidationPattern() {}
}
