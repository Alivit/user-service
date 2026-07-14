package com.minispring.userservice.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.request.AdminUserUpdateRequest;
import com.minispring.userservice.dto.request.UserUpdateRequest;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class UserCacheIT extends BaseIntegrationTest {

    @Value("${app.security.jwt.blacklist.prefix}")
    private String blacklistPrefix;

    @Value("${app.security.jwt.blacklist.ttl}")
    private Duration blacklistTtl;

    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private User createAndSaveUser(boolean active) {
        User user = Instancio.of(User.class)
                .ignore(field("version"))
                .ignore(field("cards"))
                .set(field(User::getActive), active)
                .create();
        return userRepository.saveAndFlush(user);
    }

    @Nested
    class GetUserCacheTest {

        @Test
        void shouldCacheUserData() {
            User user = createAndSaveUser(true);

            userService.getById(user.getId());

            assertCacheExists("user_info", user.getId());
        }

        @Test
        void shouldNotCacheDataWhenUserNotFound() {
            UUID unknownId = UUID.randomUUID();

            assertThatThrownBy(() -> userService.getById(unknownId)).isInstanceOf(ResourceNotFoundException.class);

            assertCacheEmpty("user_info", unknownId);
        }
    }

    @Nested
    class BlockedAndDeletedUserCacheTest {

        @Test
        void shouldNotCacheGetByIdForBlockedUser() {
            User user = createAndSaveUser(false);

            assertThatThrownBy(() -> userService.getById(user.getId()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldNotCacheGetByIdForDeletedUser() {
            User user = createAndSaveUser(true);

            userRepository.deleteById(user.getId());

            assertThatThrownBy(() -> userService.getById(user.getId())).isInstanceOf(ResourceNotFoundException.class);

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheOnUpdateIfUserIsBlocked() {
            User user = createAndSaveUser(true);
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            user.setActive(false);
            userRepository.saveAndFlush(user);

            UserUpdateRequest updateDto = new UserUpdateRequest("NewName", null);

            assertThatThrownBy(() -> userService.update(user.getId(), updateDto))
                    .isInstanceOf(BadRequestException.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class UpdateAdminCacheTest {

        static Stream<AdminUserUpdateRequest> provideAdminUpdateRequests() {
            return Stream.of(
                    new AdminUserUpdateRequest("NewName", "NewSurname", LocalDate.of(1990, 1, 1)),
                    new AdminUserUpdateRequest("NewName", "NewSurname", null),
                    new AdminUserUpdateRequest("NewName", null, LocalDate.of(1995, 5, 20)),
                    new AdminUserUpdateRequest(null, "NewSurname", LocalDate.of(1995, 5, 20)),
                    new AdminUserUpdateRequest("NewName", null, null),
                    new AdminUserUpdateRequest(null, "NewSurname", null),
                    new AdminUserUpdateRequest(null, null, LocalDate.of(1995, 5, 20)),
                    new AdminUserUpdateRequest(null, null, null),
                    null);
        }

        @ParameterizedTest
        @MethodSource("provideAdminUpdateRequests")
        void shouldEvictCacheWhenValidRequest(AdminUserUpdateRequest request) {
            User user = createAndSaveUser(true);

            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            userService.update(user.getId(), request);

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheWhenUserNotFound() {
            User user = createAndSaveUser(true);
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            AdminUserUpdateRequest adminUpdateDto =
                    new AdminUserUpdateRequest("AdminName", "AdminSurname", LocalDate.now());
            UUID unknownId = UUID.randomUUID();

            assertThatThrownBy(() -> userService.update(unknownId, adminUpdateDto))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class UpdateUserCacheTest {

        static Stream<UserUpdateRequest> provideUserUpdateRequests() {
            return Stream.of(
                    new UserUpdateRequest("NewName", "NewSurname"),
                    new UserUpdateRequest("NewName", null),
                    new UserUpdateRequest(null, "NewSurname"),
                    new UserUpdateRequest(null, null),
                    null);
        }

        @ParameterizedTest
        @MethodSource("provideUserUpdateRequests")
        void shouldEvictCacheWhenValidRequest(UserUpdateRequest request) {
            User user = createAndSaveUser(true);

            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            userService.update(user.getId(), request);

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheWhenUserNotFound() {
            User user = createAndSaveUser(true);
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            UserUpdateRequest updateDto = new UserUpdateRequest("NewName", "NewSurname");
            UUID unknownId = UUID.randomUUID();

            assertThatThrownBy(() -> userService.update(unknownId, updateDto))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class DeleteUserCacheTest {

        @Test
        void shouldNotCacheDataWhenUserIsDeleted() {
            User user = createAndSaveUser(true);
            userService.delete(user.getId());

            assertThatThrownBy(() -> userService.getById(user.getId())).isInstanceOf(ResourceNotFoundException.class);

            assertCacheEmpty("user_info", user.getId());
            assertBlacklistExists(user.getId());
        }

        @Test
        void shouldNotEvictUserCacheWhenExceptionThrown() {
            User user = createAndSaveUser(true);
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            assertThatThrownBy(() -> userService.delete(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class DeactivateCacheTest {

        @Test
        void shouldEvictCachesAndBanTokenWhenUserStatusChanges() {
            User user = createAndSaveUser(true);
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            userService.deactivate(user.getId());

            assertCacheEmpty("user_info", user.getId());
            assertThat(userRepository.findById(user.getId()).get().getActive()).isFalse();

            assertBlacklistExists(user.getId());
        }

        @Test
        void shouldThrowNotFoundException() {
            User user = createAndSaveUser(true);
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            assertThatThrownBy(() -> userService.deactivate(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class ActivateCacheTest {

        @Test
        void shouldEvictCacheAndUnbanTokenWhenUserActivated() {
            User user = createAndSaveUser(false);

            cacheManager.getCache("user_info").put(user.getId(), "old_dirty_data");
            assertCacheExists("user_info", user.getId());

            stringRedisTemplate
                    .opsForValue()
                    .set(blacklistPrefix + ":" + user.getId().toString(), "true", blacklistTtl);
            assertBlacklistExists(user.getId());

            userService.activate(user.getId());

            assertCacheEmpty("user_info", user.getId());
            assertBlacklistEmpty(user.getId());
        }

        @Test
        void shouldEvictCacheEvenIfAlreadyActive() {
            User user = createAndSaveUser(true);

            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            userService.activate(user.getId());

            assertCacheEmpty("user_info", user.getId());
        }
    }

    private void assertCacheExists(String cacheName, UUID key) {
        Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    boolean existsAsUuid = cache.get(key) != null;
                    boolean existsAsString = cache.get(key.toString()) != null;
                    assertThat(existsAsUuid || existsAsString)
                            .as("Cache '%s' should contain data for key: %s", cacheName, key)
                            .isTrue();
                });
    }

    private void assertCacheEmpty(String cacheName, UUID key) {
        Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(cache.get(key))
                            .as("Cache '%s' still contains data for UUID key", cacheName)
                            .isNull();
                    assertThat(cache.get(key.toString()))
                            .as("Cache '%s' still contains data for String key", cacheName)
                            .isNull();
                });
    }

    private void assertBlacklistExists(UUID userId) {
        String key = blacklistPrefix + ":" + userId.toString();
        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(stringRedisTemplate.hasKey(key))
                            .as("JWT Blacklist key '%s' should exist in Redis", key)
                            .isTrue();

                    Long expireInSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);

                    assertThat(expireInSeconds)
                            .as("JWT Blacklist key should have a valid TTL in Redis")
                            .isNotNull()
                            .isGreaterThan(0L)
                            .isCloseTo(blacklistTtl.getSeconds(), org.assertj.core.data.Offset.offset(5L));
                });
    }

    private void assertBlacklistEmpty(UUID userId) {
        String key = blacklistPrefix + ":" + userId.toString();
        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(stringRedisTemplate.hasKey(key))
                            .as("JWT Blacklist key '%s' should NOT exist in Redis", key)
                            .isFalse();
                });
    }
}
