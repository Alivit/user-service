package com.minispring.userservice.cache;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PaymentCardCacheIT extends BaseIntegrationTest {

    @Autowired
    private PaymentCardService paymentCardService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentCardRepository paymentCardRepository;

    private User user;
    private UUID userId;

    @BeforeEach
    void init() {
        cacheManager.getCacheNames().forEach(name ->
                Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)
        );

        User rawUser = new User();
        rawUser.setId(UUID.randomUUID());
        rawUser.setName("TestName");
        rawUser.setSurname("TestSurname");
        rawUser.setEmail("test@example.com");
        rawUser.setBirthDate(LocalDate.of(1995, 5, 20));
        rawUser.setActive(true);

        user = userRepository.saveAndFlush(rawUser);
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        paymentCardRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private void createUserCache() {
        Objects.requireNonNull(cacheManager.getCache("user_info")).put(userId, "dummy_user");
    }

    private PaymentCard createAndSaveValidCard() {
        PaymentCard card = new PaymentCard();
        card.setUser(user);
        card.setNumber("1111222233334444");
        card.setHolder("TEST SURNAME");
        card.setExpirationDate(YearMonth.parse("2030-12"));
        card.setActive(true);
        return paymentCardRepository.saveAndFlush(card);
    }

    private PaymentCard createAndSaveCardWithNumber(String number) {
        PaymentCard card = new PaymentCard();
        card.setUser(user);
        card.setNumber(number);
        card.setHolder("TEST SURNAME");
        card.setExpirationDate(YearMonth.parse("2030-12"));
        card.setActive(true);
        return paymentCardRepository.saveAndFlush(card);
    }

    private PaymentCard createAndSaveValidInactiveCard() {
        PaymentCard card = new PaymentCard();
        card.setUser(user);
        card.setNumber("5555666677778888");
        card.setHolder("TEST SURNAME");
        card.setExpirationDate(YearMonth.parse("2030-12"));
        card.setActive(false);
        return paymentCardRepository.saveAndFlush(card);
    }

    @Nested
    class CreateCardCacheTest {

        private PaymentCardCreateDto createDto;

        @BeforeEach
        public void initCreate(){
            createDto = new PaymentCardCreateDto("1234567890123456", "TEST SURNAME", YearMonth.parse("2031-12"));
        }

        @Test
        void createShouldEvictCacheWhenSuccess() {
            createUserCache();
            paymentCardService.create(userId, createDto);
            assertCacheEmpty("user_info", userId);
        }

        @Test
        void createShouldNotEvictCacheWhenLimitExceeded() {
            for (int i = 0; i < 5; i++) {
                createAndSaveCardWithNumber("444455556666000" + i);
            }

            createUserCache();

            assertThrows(BadRequestException.class, () ->
                    paymentCardService.create(userId, createDto));

            assertCacheExists("user_info", userId);
        }
    }

    @Nested
    class UpdateCacheTest {

        @Test
        void updateShouldEvictCacheWhenDataChanged() {
            PaymentCard card = createAndSaveValidCard();
            createUserCache();

            PaymentCardUpdateDto updateDto = new PaymentCardUpdateDto(null, "NEW HOLDER", YearMonth.parse("2035-05"), true);
            paymentCardService.update(card.getId(), updateDto);

            assertCacheEmpty("user_info", card.getId());
        }

        @Test
        void updateShouldThrowExceptionAndNotEvictCacheWhenCardNotFound() {
            UUID randomId = UUID.randomUUID();
            createUserCache();
            PaymentCardUpdateDto dto = new PaymentCardUpdateDto(null, "FAIL NAME", YearMonth.parse("2035-05"), true);

            assertThrows(ResourceNotFoundException.class, () ->
                    paymentCardService.update(randomId, dto)
            );

            assertCacheExists("user_info", userId);
        }

    }

    @Nested
    class DeleteCacheTest {

        @Test
        void deleteWithUserIdShouldEvictAllRelatedCachesOnSuccess() {
            PaymentCard card = createAndSaveValidCard();
            createUserCache();

            paymentCardService.delete(userId, card.getId());

            assertCacheEmpty("user_info", userId);
        }

        @Test
        void deleteShouldEvictAllRelatedCachesOnSuccess() {
            PaymentCard card = createAndSaveValidCard();
            createUserCache();

            paymentCardService.delete(card.getId());

            assertCacheEmpty("user_info", userId);
        }

        @Test
        void shouldWithUserIdNotEvictUserCacheWhenExceptionThrown() {
            createUserCache();

            assertThrows(ResourceNotFoundException.class, () ->
                    paymentCardService.delete(userId, UUID.randomUUID())
            );

            assertCacheExists("user_info", userId);
        }

        @Test
        void shouldWithNotEvictUserCacheWhenExceptionThrown() {
            createUserCache();

            assertThrows(ResourceNotFoundException.class, () ->
                    paymentCardService.delete(UUID.randomUUID())
            );

            assertCacheExists("user_info", userId);
        }
    }

    @Nested
    class DeactivateCacheTestTest {

        @Test
        void deactivateShouldEvictUserCacheWhenCardExists() {
            PaymentCard card = createAndSaveValidCard();
            createUserCache();

            paymentCardService.deactivate(card.getId());

            assertCacheEmpty("user_info", userId);
            assertThat(paymentCardRepository.findById(card.getId()).get().getActive()).isFalse();
        }

        @Test
        void deactivateWithUserIdShouldEvictUserCacheWhenCardExists() {
            PaymentCard card = createAndSaveValidCard();
            createUserCache();

            paymentCardService.deactivate(userId, card.getId());

            assertCacheEmpty("user_info", userId);
            assertThat(paymentCardRepository.findById(card.getId()).get().getActive()).isFalse();
        }

        @Test
        void deactivateWithUserIdShouldThrowExceptionWhenCardNotFound() {
            createUserCache();
            UUID cardInvalidId = UUID.randomUUID();

            assertThrows(ResourceNotFoundException.class, () ->
                    paymentCardService.deactivate(cardInvalidId, userId));

            assertCacheExists("user_info", userId);
        }

        @Test
        void deactivateShouldThrowExceptionWhenCardNotFound() {
            createUserCache();
            UUID cardInvalidId = UUID.randomUUID();

            assertThrows(ResourceNotFoundException.class, () ->
                    paymentCardService.deactivate(cardInvalidId));

            assertCacheExists("user_info", userId);
        }
    }

    @Nested
    class ActiveTest {

        @Test
        void activateShouldEvictUserCacheWhenCardExists() {
            PaymentCard card = createAndSaveValidInactiveCard();
            createUserCache();

            paymentCardService.activate(card.getId());

            assertCacheEmpty("user_info", userId);
            assertThat(paymentCardRepository.findById(card.getId()).get().getActive()).isTrue();
        }


        @Test
        void activateShouldThrowExceptionWhenCardNotFound() {
            createUserCache();
            UUID cardInvalidId = UUID.randomUUID();

            assertThrows(ResourceNotFoundException.class, () ->
                    paymentCardService.activate(cardInvalidId));

            assertCacheExists("user_info", userId);
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
