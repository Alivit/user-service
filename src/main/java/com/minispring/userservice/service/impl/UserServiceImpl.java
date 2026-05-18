package com.minispring.userservice.service.impl;

import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.UserMapper;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        User userBeforeSaving = userMapper.userCreateDtoToUser(userCreateDto);
        User savedUser = userRepository.save(userBeforeSaving);
        log.debug("User with id {} created successfully Email: {}", savedUser.getId(), savedUser.getEmail());
        return userMapper.userToUserProfileDto(savedUser);
    }

    @Override
    public UserProfileDto getById(UUID userId) {
        User foundedUser = getExistingUser(userId);
        log.debug("User with id {} has been found", userId);
        return userMapper.userToUserProfileDto(foundedUser);
    }

    @Override
    public Page<UserProfileDto> getAllBy(UserParamsDto userParamsDto, Pageable pageable) {
        return userRepository.findByParams(userParamsDto, pageable).map(userMapper::userToUserProfileDto);
    }

    @Override
    public List<UserProfileDto> getAll() {
        return userRepository.findAll().stream().map(userMapper::userToUserProfileDto).toList();
    }

    @Transactional
    @Override
    public UserProfileDto update(UUID userId, UserUpdateDto userUpdateDto) {
        User existingUser = getExistingUser(userId);
        UserUpdateDto userStateBefore = userMapper.userToUserUpdateDto(existingUser);
        userMapper.updateUserFromDto(userUpdateDto, existingUser);
        UserUpdateDto userStateAfter = userMapper.userToUserUpdateDto(existingUser);
        Diff diff = javers.compare(userStateBefore, userStateAfter);

        if (diff.hasChanges()) {
            log.debug("User {} have changes: {}", userId, diff.prettyPrint());
        }
        return userMapper.userToUserProfileDto(existingUser);
    }

    @Transactional
    @Override
    public UserProfileDto setActive(UUID userId) {
        User existingUser = getExistingUser(userId);
        existingUser.setActive(!existingUser.getActive());
        log.debug("User ID: {}. Active status changed to: {}", userId, existingUser.getActive());
        return userMapper.userToUserProfileDto(existingUser);
    }

    private User getExistingUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
    }
}
