package com.minispring.userservice.cache;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
import com.minispring.userservice.client.AuthGrpcClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "app.audit.enabled=false")
public class UserCacheIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void init() {
        cacheManager.getCacheNames().forEach(name ->
                Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)
        );
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    private User createAndSaveActiveUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Ivan");
        user.setSurname("Ivanov");
        user.setEmail(email);
        user.setBirthDate(LocalDate.of(1995, 5, 20));
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private User createAndSaveInactiveUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Petr");
        user.setSurname("Petrov");
        user.setEmail(email);
        user.setBirthDate(LocalDate.of(1993, 3, 15));
        user.setActive(false);
        return userRepository.saveAndFlush(user);
    }

    @Nested
    class GetUserCacheTest {

        @Test
        void getByIdShouldCacheData() {
            User user = createAndSaveActiveUser("test@example.com");

            userService.getById(user.getId());

            assertCacheExists("user_info", user.getId());
        }

        @Test
        void getByIdShouldThrowExceptionWhenUserNotFound() {
            User user = createAndSaveActiveUser("test@example.com");

            assertThrows(ResourceNotFoundException.class, () ->
                    userService.getById(UUID.randomUUID()));

            assertCacheEmpty("user_info", user.getId());
        }
    }

    @Nested
    class UpdateUserCacheTest {

        @Test
        void updateShouldEvictCacheWhenDataChanged() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            UserUpdateDto updateDto = new UserUpdateDto("NewName", "NewSurname");
            userService.update(user.getId(), updateDto);

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void updateWithAdminDtoShouldEvictCacheWhenDataChanged() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            AdminUserUpdateDto adminUpdateDto = new AdminUserUpdateDto(
                    "AdminName",
                    "AdminSurname",
                    null);
            userService.update(user.getId(), adminUpdateDto);

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void updateShouldThrowExceptionAndNotEvictCacheWhenUserNotFound() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            UserUpdateDto updateDto = new UserUpdateDto("NewName", "NewSurname");

            assertThrows(ResourceNotFoundException.class, () ->
                    userService.update(UUID.randomUUID(), updateDto)
            );

            assertCacheExists("user_info", user.getId());
        }

        @Test
        void updateWithAdminShouldThrowExceptionAndNotEvictCacheWhenUserNotFound() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            AdminUserUpdateDto adminUpdateDto = new AdminUserUpdateDto(
                    "AdminName",
                    "AdminSurname",
                    null);
            assertThrows(ResourceNotFoundException.class, () ->
                    userService.update(UUID.randomUUID(), adminUpdateDto)
            );

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class DeleteUserCacheTest {

        @Test
        void deleteShouldEvictUserCacheOnSuccess() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            userService.delete(user.getId());

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldWithNotEvictUserCacheWhenExceptionThrown() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            assertThrows(ResourceNotFoundException.class, () ->
                    userService.delete(UUID.randomUUID())
            );

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class DeactivateCacheTest {

        @MockitoBean
        protected AuthGrpcClient authGrpcClient;

        @Test
        void deactivateShouldEvictCachesWhenUserExists() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            userService.deactivate(user.getId());

            assertCacheEmpty("user_info", user.getId());
            assertThat(userRepository.findById(user.getId()).get().getActive()).isFalse();
        }

        @Test
        void deactivateShouldThrowExceptionWhenUserNotFound() {
            User user = createAndSaveActiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            assertThrows(ResourceNotFoundException.class, () ->
                    userService.deactivate(UUID.randomUUID()));

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class ActivateCacheTest {

        @MockitoBean
        protected AuthGrpcClient authGrpcClient;

        @Test
        void activateShouldEvictCachesWhenUserExists() {
            User user = createAndSaveInactiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            userService.activate(user.getId());

            assertCacheEmpty("user_info", user.getId());
            assertThat(userRepository.findById(user.getId()).get().getActive()).isTrue();
        }

        @Test
        void activateShouldThrowExceptionWhenUserNotFound() {
            User user = createAndSaveInactiveUser("test@example.com");
            userService.getById(user.getId());
            assertCacheExists("user_info", user.getId());

            assertThrows(ResourceNotFoundException.class, () ->
                    userService.activate(UUID.randomUUID()));

            assertCacheExists("user_info", user.getId());
        }
    }

    private void assertCacheExists(String cacheName, UUID key) {
        Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).pollInterval(10, TimeUnit.MILLISECONDS)
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

        Awaitility.await().atMost(2, TimeUnit.SECONDS).pollInterval(10, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(cache.get(key))
                            .as("Cache '%s' still contains data for UUID key", cacheName)
                            .isNull();
                    assertThat(cache.get(key.toString()))
                            .as("Cache '%s' still contains data for String key", cacheName)
                            .isNull();
                });
    }
}
