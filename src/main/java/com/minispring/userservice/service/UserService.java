package com.minispring.userservice.service;

import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    UserProfileDto create(UserCreateDto userCreateDto);

    UserProfileDto getById(UUID userId);

    User getExistingUser(UUID userId);

    Page<UserProfileDto> getAllBy(UserParamsDto userParamsDto, Pageable pageable);

    UserProfileDto update(UUID userId, AdminUserUpdateDto adminUpdateDto);

    UserProfileDto update(UUID userId, UserUpdateDto userUpdateDto);

    UserProfileDto deactivate(UUID userId);

    UserProfileDto activate(UUID userId);
}
