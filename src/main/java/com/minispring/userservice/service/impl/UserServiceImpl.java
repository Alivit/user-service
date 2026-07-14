package com.minispring.userservice.service.impl;

import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_EXIST;
import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_NOT_FOUND;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;

import com.minispring.userservice.client.AuthGrpcClient;
import com.minispring.userservice.dto.request.AdminUserUpdateRequest;
import com.minispring.userservice.dto.request.UserCreateRequest;
import com.minispring.userservice.dto.request.UserSearchCriteria;
import com.minispring.userservice.dto.request.UserUpdateRequest;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.UserMapper;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
import com.minispring.userservice.util.TransactionUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    @Value("${app.security.jwt.blacklist.prefix}")
    private String blacklistPrefix;

    @Value("${app.security.jwt.blacklist.ttl}")
    private Duration blacklistTtl;

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthGrpcClient authGrpcClient;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    @Override
    public UserView create(UserCreateRequest request) {
        try {
            User user = userMapper.toEntity(request);
            User saved = userRepository.saveAndFlush(user);
            log.info("User with id {} created successfully Email: {}", saved.getId(), saved.getEmail());
            return userMapper.toViewWithoutCards(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException(String.format(EMAIL_EXIST, request.email()));
        }
    }

    @Override
    @Cacheable(value = "user_info", key = "#userId")
    public UserView getById(UUID userId) {
        User user = getExistsUserWithCardsById(userId);
        validateUserAllowed(user);
        return userMapper.toView(user);
    }

    @Override
    public UserView getHistoricalUserById(UUID userId) {
        return userRepository
                .findHistoricalUserById(userId)
                .map(userMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
    }

    @Override
    public UserView getHistoricalUserByEmail(String email) {
        return userRepository
                .findHistoricalUserByEmail(email)
                .map(userMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(EMAIL_NOT_FOUND, email)));
    }

    @Override
    public List<UserView> getHistoricalUsersByIds(List<UUID> userIds) {
        return userRepository.findHistoricalUsersByIds(userIds).stream()
                .map(userMapper::toView)
                .toList();
    }

    @Override
    public Page<UserView> getAllBy(UserSearchCriteria userSearchCriteria, Pageable pageable) {
        return userRepository.findByParams(userSearchCriteria, pageable).map(userMapper::toView);
    }

    @Override
    public User getValidUserEntity(UUID userId) {
        User user = getExistsUserById(userId);
        validateUserAllowed(user);
        return user;
    }

    @Override
    public User getValidUserEntityForUpdate(UUID userId) {
        User user = userRepository
                .findUserForUpdateById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
        validateUserAllowed(user);
        return user;
    }

    @Override
    public User getUserReference(UUID userId) {
        return userRepository.getReferenceById(userId);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserView update(UUID userId, UserUpdateRequest request) {
        User existingUser = getExistsUserById(userId);
        validateUserAllowed(existingUser);
        userMapper.update(request, existingUser);
        return userMapper.toViewWithoutCards(existingUser);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserView update(UUID userId, AdminUserUpdateRequest request) {
        User existingUser = getExistsUserById(userId);
        userMapper.update(request, existingUser);
        return userMapper.toViewWithoutCards(existingUser);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public void delete(UUID userId) {
        User user = getExistsUserWithCardsById(userId);
        validateUserAllowed(user);
        banUserToken(userId);
        userRepository.delete(user);
        TransactionUtils.afterCommit(() -> authGrpcClient.deleteUser(userId));
        log.info("User {} successfully deleted", userId);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserView deactivate(UUID userId) {
        User user = getExistsUserById(userId);
        if (user.getActive()) {
            user.setActive(false);
            user.setUpdatedAt(Instant.now());
            banUserToken(userId);
            TransactionUtils.afterCommit(() -> authGrpcClient.setStatus(userId, false));
            log.info("User ID: {} has been banned", userId);
        }
        return userMapper.toViewWithoutCards(user);
    }

    @Transactional
    @Override
    @CacheEvict(value = "user_info", key = "#userId")
    public UserView activate(UUID userId) {
        User user = getExistsUserById(userId);
        if (!user.getActive()) {
            user.setActive(true);
            user.setUpdatedAt(Instant.now());
            unbanUserToken(userId);
            TransactionUtils.afterCommit(() -> authGrpcClient.setStatus(userId, true));
            log.info("User ID: {} has been unbanned", userId);
        }
        return userMapper.toViewWithoutCards(user);
    }

    public void validateUserAllowed(User user) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return;
        }

        if (Boolean.FALSE.equals(user.getActive()) || user.isDeleted()) {
            throw new BadRequestException("Action denied: User is blocked or deleted");
        }
    }

    private User getExistsUserWithCardsById(UUID userId) {
        return userRepository
                .findUserWithCardsById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
    }

    private User getExistsUserById(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));
    }

    private void banUserToken(UUID userId) {
        stringRedisTemplate.opsForValue().set(blacklistPrefix + ":" + userId.toString(), "true", blacklistTtl);
        log.debug("User {} added to JWT blacklist in Redis with TTL {} seconds", userId, blacklistTtl);
    }

    private void unbanUserToken(UUID userId) {
        String redisKey = blacklistPrefix + ":" + userId.toString();
        stringRedisTemplate.delete(redisKey);
        log.debug("User {} removed from JWT blacklist in Redis", userId);
    }
}
