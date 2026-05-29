package com.minispring.userservice.service;

import com.minispring.grpc.service.UserDto;
import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserProfileDto create(UserCreateDto userCreateDto);

    UserProfileDto getById(UUID userId);

    UserDto getByIdForGrpc(UUID userId);

    UserDto getByEmailForGrpc(String email);

    List<UserDto> getUsersByIdsForGrpc(List<UUID> userIds);

    Page<UserProfileDto> getAllBy(UserParamsDto userParamsDto, Pageable pageable);

    UserProfileDto update(UUID userId, AdminUserUpdateDto adminUpdateDto);

    UserProfileDto update(UUID userId, UserUpdateDto userUpdateDto);

    void delete(UUID userId);

    UserProfileDto deactivate(UUID userId);

    UserProfileDto activate(UUID userId);
}
