package com.minispring.userservice.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.request.PaymentCardUpdateRequest;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
import java.time.YearMonth;
import java.util.Objects;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PaymentCardCacheIT extends BaseIntegrationTest {

    @Autowired
    private PaymentCardService paymentCardService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentCardRepository paymentCardRepository;

    @AfterEach
    void tearDown() {
        paymentCardRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndSaveUser() {
        User user = Instancio.of(User.class)
                .ignore(field("version"))
                .ignore(field("cards"))
                .set(field(User::getActive), true)
                .create();
        return userRepository.saveAndFlush(user);
    }

    private PaymentCard createAndSaveCard(User user, boolean active) {
        PaymentCard card = new PaymentCard();
        card.setUser(user);
        card.setNumber("111122223333" + (int) (Math.random() * 9000 + 1000));
        card.setHolder("TEST SURNAME");
        card.setCardHash(UUID.randomUUID().toString());
        card.setExpirationDate(YearMonth.of(2030, 12));
        card.setActive(active);
        return paymentCardRepository.saveAndFlush(card);
    }

    private void seedUserCache(UUID userId) {
        Objects.requireNonNull(cacheManager.getCache("user_info")).put(userId, "dummy_user_data");
    }

    @Nested
    class CreateCardCacheTest {

        @Test
        void shouldEvictCacheWhenSuccess() {
            User user = createAndSaveUser();
            seedUserCache(user.getId());
            PaymentCardCreateRequest createDto =
                    new PaymentCardCreateRequest("1234567890123456", "TEST SURNAME", YearMonth.of(2031, 12));

            paymentCardService.create(user.getId(), createDto);

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheWhenLimitExceeded() {
            User user = createAndSaveUser();
            for (int i = 0; i < 5; i++) {
                createAndSaveCard(user, true);
            }
            seedUserCache(user.getId());
            PaymentCardCreateRequest createDto =
                    new PaymentCardCreateRequest("1234567890123456", "TEST SURNAME", YearMonth.of(2031, 12));

            assertThatThrownBy(() -> paymentCardService.create(user.getId(), createDto))
                    .isInstanceOf(BadRequestException.class);

            assertCacheExists("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheWhenUserIsBlocked() {
            User user = createAndSaveUser();

            user.setActive(false);
            userRepository.saveAndFlush(user);

            seedUserCache(user.getId());
            PaymentCardCreateRequest createDto =
                    new PaymentCardCreateRequest("1234567890123456", "TEST SURNAME", YearMonth.of(2031, 12));

            assertThatThrownBy(() -> paymentCardService.create(user.getId(), createDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class UpdateCacheTest {

        static Stream<PaymentCardUpdateRequest> provideUpdateRequests() {
            return Stream.of(
                    new PaymentCardUpdateRequest("1234123412341234", null, null),
                    new PaymentCardUpdateRequest(null, "NEW HOLDER", null),
                    new PaymentCardUpdateRequest(null, null, YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest("1234123412341234", "NEW HOLDER", null),
                    new PaymentCardUpdateRequest(
                            "1234123412341234", null, YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest(
                            null, "NEW HOLDER", YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest(
                            "1234123412341234", "NEW HOLDER", YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest(null, null, null),
                    new PaymentCardUpdateRequest("9999888877776666", "TEST NAME", YearMonth.of(2030, 1)));
        }

        @ParameterizedTest
        @MethodSource("provideUpdateRequests")
        void shouldEvictCacheWhenValidRequest(PaymentCardUpdateRequest request) {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, true);
            seedUserCache(user.getId());

            paymentCardService.update(card.getId(), request);

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheWhenCardNotFound() {
            User user = createAndSaveUser();
            seedUserCache(user.getId());
            UUID unknownCardId = UUID.randomUUID();
            PaymentCardUpdateRequest request = new PaymentCardUpdateRequest(null, "FAIL NAME", YearMonth.of(2035, 5));

            assertThatThrownBy(() -> paymentCardService.update(unknownCardId, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class DeleteCacheTest {

        @Test
        void shouldEvictUserCacheOnSuccessByUser() {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, true);
            seedUserCache(user.getId());

            paymentCardService.delete(user.getId(), card.getId());

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldEvictUserCacheOnSuccessByAdmin() {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, true);
            seedUserCache(user.getId());

            paymentCardService.delete(card.getId());

            assertCacheEmpty("user_info", user.getId());
        }

        @Test
        void shouldNotEvictUserCacheWhenExceptionThrown() {
            User user = createAndSaveUser();
            seedUserCache(user.getId());

            assertThatThrownBy(() -> paymentCardService.delete(user.getId(), UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheWhenUserIsDeleted() {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, true);

            userRepository.deleteById(user.getId());

            seedUserCache(user.getId());

            assertThatThrownBy(() -> paymentCardService.delete(user.getId(), card.getId()))
                    .isInstanceOf(Exception.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class DeactivateCacheTestTest {

        @Test
        void shouldEvictUserCacheWhenCardExistsByUser() {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, true);
            seedUserCache(user.getId());

            paymentCardService.deactivate(user.getId(), card.getId());

            assertCacheEmpty("user_info", user.getId());
            assertThat(paymentCardRepository.findById(card.getId()).get().getActive())
                    .isFalse();
        }

        @Test
        void shouldEvictUserCacheWhenCardExistsByAdmin() {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, true);
            seedUserCache(user.getId());

            paymentCardService.deactivate(card.getId());

            assertCacheEmpty("user_info", user.getId());
            assertThat(paymentCardRepository.findById(card.getId()).get().getActive())
                    .isFalse();
        }

        @Test
        void shouldNotEvictCacheWhenCardNotFound() {
            User user = createAndSaveUser();
            seedUserCache(user.getId());

            assertThatThrownBy(() -> paymentCardService.deactivate(user.getId(), UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
        }

        @Test
        void shouldNotEvictCacheWhenUserIsBlocked() {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, true);

            user.setActive(false);
            userRepository.saveAndFlush(user);

            seedUserCache(user.getId());

            assertThatThrownBy(() -> paymentCardService.deactivate(user.getId(), card.getId()))
                    .isInstanceOf(BadRequestException.class);

            assertCacheExists("user_info", user.getId());
        }
    }

    @Nested
    class ActiveTest {

        @Test
        void shouldEvictUserCacheWhenCardExists() {
            User user = createAndSaveUser();
            PaymentCard card = createAndSaveCard(user, false);
            seedUserCache(user.getId());

            paymentCardService.activate(card.getId());

            assertCacheEmpty("user_info", user.getId());
            assertThat(paymentCardRepository.findById(card.getId()).get().getActive())
                    .isTrue();
        }

        @Test
        void shouldNotEvictCacheWhenCardNotFound() {
            User user = createAndSaveUser();
            seedUserCache(user.getId());

            assertThatThrownBy(() -> paymentCardService.activate(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertCacheExists("user_info", user.getId());
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
}
