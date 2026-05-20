package com.minispring.userservice.service.impl;

import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.UserMapper;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_EXIST;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final Javers javers;

    @Transactional
    @Override
    public UserProfileDto create(UserCreateDto userCreateDto) {
        if (userRepository.existsByEmail(userCreateDto.email())) {
            throw new ResourceAlreadyExistsException(String.format(EMAIL_EXIST, userCreateDto.email()));
        }
        User userBeforeSaving = userMapper.userCreateDtoToUser(userCreateDto);
        User savedUser = userRepository.saveAndFlush(userBeforeSaving);
        log.debug("User with id {} created successfully Email: {}", savedUser.getId(), savedUser.getEmail());
        return userMapper.userToUserProfileDto(savedUser);
    }

    @Override
    @Cacheable(value = "user_info", key = "#userId")
    public UserProfileDto getById(UUID userId) {
        User foundedUser = getExistingUser(userId);
        log.debug("User with id {} has been found", userId);
        return userMapper.userToUserProfileDto(foundedUser);
    }

    @Override
    public Page<UserProfileDto> getAllBy(UserParamsDto userParamsDto, Pageable pageable) {
        return userRepository.findByParams(userParamsDto, pageable).map(userMapper::userToUserProfileDto);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserProfileDto update(UUID userId, UserUpdateDto userUpdateDto) {
        User existingUser = getExistingUser(userId);
        UserUpdateDto userStateBefore = userMapper.userToUserUpdateDto(existingUser);
        userMapper.updateUserFromDto(userUpdateDto, existingUser);
        userRepository.flush();
        UserUpdateDto userStateAfter = userMapper.userToUserUpdateDto(existingUser);
        Diff diff = javers.compare(userStateBefore, userStateAfter);

        if (diff.hasChanges()) {
            log.debug("User {} have changes: {}", userId, diff.prettyPrint());
        }
        return userMapper.userToUserProfileDto(existingUser);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserProfileDto update(UUID userId, AdminUserUpdateDto userUpdateDto) {
        User existingUser = getExistingUser(userId);
        AdminUserUpdateDto userStateBefore = userMapper.userToAdminUserUpdateDto(existingUser);
        userMapper.updateUserFromDto(userUpdateDto, existingUser);
        userRepository.flush();
        AdminUserUpdateDto userStateAfter = userMapper.userToAdminUserUpdateDto(existingUser);
        Diff diff = javers.compare(userStateBefore, userStateAfter);

        if (diff.hasChanges()) {
            log.debug("User {} have changes: {}", userId, diff.prettyPrint());
        }
        return userMapper.userToUserProfileDto(existingUser);
    }

    @Transactional
    @Override
    @Caching(evict = {
            @CacheEvict(value = "user_info", key = "#userId"),
            @CacheEvict(value = "user_cards", key = "#userId")
    })
    public UserProfileDto deactivate(UUID userId) {
        User existingUser = getExistingUser(userId);
        existingUser.setActive(false);
        userRepository.flush();
        log.debug("User ID: {} has been banned", userId);
        return userMapper.userToUserProfileDto(existingUser);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserProfileDto activate(UUID userId) {
        User existingUser = getExistingUser(userId);
        existingUser.setActive(true);
        userRepository.flush();
        log.debug("User ID: {} has been unbanned", userId);
        return userMapper.userToUserProfileDto(existingUser);
    }

    public User getExistingUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
    }
}
