package com.minispring.userservice.service.impl;

import com.minispring.grpc.service.UserDto;
import com.minispring.userservice.client.AuthGrpcClient;
import com.minispring.userservice.config.JaversConfig.AuditProperties;
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
import com.minispring.userservice.service.listener.AuditUpdateEvent;
import com.minispring.userservice.util.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_EXIST;
import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_NOT_FOUND;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final ApplicationEventPublisher eventPublisher;
    private final AuditProperties auditProperties;
    private final AuthGrpcClient authGrpcClient;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    @Override
    public UserProfileDto create(UserCreateDto userCreateDto) {
        try {
            User user = userMapper.userCreateDtoToUser(userCreateDto);
            User saved = userRepository.saveAndFlush(user);
            log.info("User with id {} created successfully Email: {}", saved.getId(), saved.getEmail());
            return userMapper.userToUserProfileDtoWithoutCards(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException(String.format(EMAIL_EXIST, userCreateDto.email()));
        }
    }

    @Override
    @Cacheable(value = "user_info", key = "#userId")
    public UserProfileDto getById(UUID userId) {
        return userMapper.userToUserProfileDto(getExistsUserWithCardsById(userId));
    }

    @Override
    public UserDto getByIdForGrpc(UUID userId) {
        return userMapper.userToGrpcUserDto(userRepository.findUserWithCardsById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId))));
    }

    @Override
    public UserDto getByEmailForGrpc(String email) {
        return userMapper.userToGrpcUserDto(userRepository.findUserByEmailIncludingDeleted(email)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(EMAIL_NOT_FOUND, email))));
    }

    @Override
    public List<UserDto> getUsersByIdsForGrpc(List<UUID> userIds) {
        return userRepository.findAllByIdsIncludingDeleted(userIds).stream()
                .map(userMapper::userToGrpcUserDto)
                .toList();
    }

    @Override
    public Page<UserProfileDto> getAllBy(UserParamsDto userParamsDto, Pageable pageable) {
        return userRepository.findByParams(userParamsDto, pageable).map(userMapper::userToUserProfileDto);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserProfileDto update(UUID userId, UserUpdateDto userUpdateDto) {
        User existingUser = getExistsUserById(userId);
        if (!auditProperties.isEnabled()) {
            userMapper.updateUserFromDto(userUpdateDto, existingUser);
            return userMapper.userToUserProfileDtoWithoutCards(existingUser);
        }
        return processUpdateWithAudit(existingUser, userUpdateDto);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserProfileDto update(UUID userId, AdminUserUpdateDto userUpdateDto) {
        User existingUser = getExistsUserById(userId);
        if (!auditProperties.isEnabled()) {
            userMapper.updateUserFromDto(userUpdateDto, existingUser);
            return userMapper.userToUserProfileDtoWithoutCards(existingUser);
        }
        return processUpdateWithAudit(existingUser, userUpdateDto);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public void delete(UUID userId) {
        User user = getExistsUserWithCardsById(userId);
        authGrpcClient.deleteUser(userId);
        userRepository.delete(user);
        log.info("User {} successfully deleted", userId);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserProfileDto deactivate(UUID userId) {
        User user = getExistsUserById(userId);
        if (user.getActive()) {
            user.setActive(false);
            user.setUpdatedAt(Instant.now());
            TransactionUtils.afterCommit(() -> authGrpcClient.setStatus(userId, false));
            log.info("User ID: {} has been banned", userId);
        }
        return userMapper.userToUserProfileDtoWithoutCards(user);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserProfileDto activate(UUID userId) {
        User user = getExistsUserById(userId);
        if (!user.getActive()) {
            user.setActive(true);
            user.setUpdatedAt(Instant.now());
            TransactionUtils.afterCommit(() -> authGrpcClient.setStatus(userId, true));
            log.info("User ID: {} has been unbanned", userId);
        }
        return userMapper.userToUserProfileDtoWithoutCards(user);
    }

    private User getExistsUserWithCardsById(UUID userId) {
        return userRepository.findUserWithCardsById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
    }

    private UserProfileDto processUpdateWithAudit(User user, UserUpdateDto dto) {
        UserUpdateDto stateBefore = userMapper.userToUserUpdateDto(user);

        userMapper.updateUserFromDto(dto, user);
        userRepository.flush();

        UserUpdateDto stateAfter = userMapper.userToUserUpdateDto(user);

        eventPublisher.publishEvent(new AuditUpdateEvent(user.getId(), stateBefore, stateAfter));
        return userMapper.userToUserProfileDtoWithoutCards(user);
    }

    private UserProfileDto processUpdateWithAudit(User user, AdminUserUpdateDto dto) {
        AdminUserUpdateDto stateBefore = userMapper.userToAdminUserUpdateDto(user);

        userMapper.updateUserFromDto(dto, user);
        userRepository.flush();

        AdminUserUpdateDto stateAfter = userMapper.userToAdminUserUpdateDto(user);

        eventPublisher.publishEvent(new AuditUpdateEvent(user.getId(), stateBefore, stateAfter));
        return userMapper.userToUserProfileDtoWithoutCards(user);
    }

    private User getExistsUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
    }
}
