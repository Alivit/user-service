package com.minispring.userservice.service;

import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserProfileDto create(UserCreateDto userCreateDto);

    UserProfileDto getById(UUID userId);

    Page<UserProfileDto> getAllBy(UserParamsDto userParamsDto, Pageable pageable);

    List<UserProfileDto> getAll();

    UserProfileDto update(UUID userId, UserUpdateDto userUpdateDto);

    UserProfileDto setActive(UUID userId);
}
