package com.minispring.userservice.exception;

public final class ExceptionAnswer {
    public static final String USER_NOT_FOUND = "User with id %s not found";
    public static final String CARD_NOT_FOUND = "Payment card with id %s not found";
    public static final String EMAIL_EXIST = "This email %s already exists";
    public static final String EMAIL_NOT_FOUND = "User with email %s not found";
    public static final String NUMBER_CARD_EXIST = "This number card already exists";
    public static final String CARD_LIMIT = "A user cannot have more than 5 bank cards";
}
