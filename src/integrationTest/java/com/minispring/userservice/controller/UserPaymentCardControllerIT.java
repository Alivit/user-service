package com.minispring.userservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.response.PaymentCardView;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
public class UserPaymentCardControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentCardService paymentCardService;

    @Autowired
    private PaymentCardRepository paymentCardRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private static final String BASE_URL = "/api/v1/cards";
    private User user;
    private PaymentCardView card;

    @BeforeEach
    public void setup() {
        user = Instancio.of(User.class)
                .ignore(field(User::getVersion))
                .ignore(field(User::getCards))
                .set(field(User::getActive), true)
                .create();
        user.setId(UUID.randomUUID());
        user = userRepository.saveAndFlush(user);

        PaymentCardCreateRequest createDto = new PaymentCardCreateRequest(
                "4111111111111111", "TEST HOLDER", YearMonth.now().plusYears(2));

        PaymentCardView created = paymentCardService.create(user.getId(), createDto);
        card = paymentCardService.getById(created.id());
    }

    @AfterEach
    void tearDown() {
        paymentCardRepository.deleteAll();
        userRepository.deleteAll();
    }

    private RequestPostProcessor userJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(user.getId().toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "regular_user"));
    }

    private RequestPostProcessor anotherUserJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "another"));
    }

    @Nested
    class CreateCardTest {

        static Stream<PaymentCardCreateRequest> provideInvalidCreateRequests() {
            return Stream.of(
                    new PaymentCardCreateRequest(
                            "4000123456789011", "VALID HOLDER", YearMonth.now().plusYears(1)),
                    new PaymentCardCreateRequest(
                            null, "VALID HOLDER", YearMonth.now().plusYears(1)),
                    new PaymentCardCreateRequest(
                            "4242424242424242", "A", YearMonth.now().plusYears(1)),
                    new PaymentCardCreateRequest(
                            "4242424242424242", "A".repeat(51), YearMonth.now().plusYears(1)),
                    new PaymentCardCreateRequest(
                            "4242424242424242", "H0LDER", YearMonth.now().plusYears(1)),
                    new PaymentCardCreateRequest(
                            "4242424242424242", null, YearMonth.now().plusYears(1)),
                    new PaymentCardCreateRequest(
                            "4242424242424242", "VALID HOLDER", YearMonth.now().minusMonths(1)),
                    new PaymentCardCreateRequest("4242424242424242", "VALID HOLDER", null));
        }

        @ParameterizedTest
        @MethodSource("provideInvalidCreateRequests")
        void createShouldReturnBadRequestWhenValidationFails(PaymentCardCreateRequest invalidDto) {
            mockMvcTester
                    .perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void createShouldReturnCreatedStatusAndLocationHeader() {
            PaymentCardCreateRequest createDto = new PaymentCardCreateRequest(
                    "4242424242424242", "NEW HOLDER", YearMonth.now().plusYears(2));

            MvcTestResult result = mockMvcTester.perform(post(BASE_URL)
                    .with(userJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto)));

            result.assertThat()
                    .hasStatus(HttpStatus.CREATED)
                    .headers()
                    .hasHeaderSatisfying(
                            "Location", values -> assertThat(values.getFirst()).contains(BASE_URL));

            result.assertThat()
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> active.assertThat().isEqualTo(true))
                    .hasPathSatisfying(
                            "$.holder", h -> h.assertThat().asString().isEqualTo("NEW HOLDER"));
        }

        @Test
        void createShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            PaymentCardCreateRequest validDto = new PaymentCardCreateRequest(
                    "4000000000000008", "NEW HOLDER", YearMonth.now().plusYears(2));

            mockMvcTester
                    .perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void createShouldReturnNotFoundWhenUserIsDeleted() {
            userRepository.deleteById(user.getId());

            PaymentCardCreateRequest validDto = new PaymentCardCreateRequest(
                    "4000000000000008", "NEW HOLDER", YearMonth.now().plusYears(2));

            mockMvcTester
                    .perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldRespectCardLimitUnderConcurrentRequests() throws Exception {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);

            String[] validCards = {
                "4000000000000002", "4000000000000010", "4000000000000028", "4000000000000036",
                "4000000000000044", "4000000000000051", "4000000000000069", "4000000000000077",
                "4000000000000085", "4000000000000093"
            };

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        PaymentCardCreateRequest validDto = new PaymentCardCreateRequest(
                                validCards[index],
                                "CONCURRENT HOLDER",
                                YearMonth.now().plusYears(2));
                        String payload = jsonMapper.writeValueAsString(validDto);

                        latch.await();
                        mockMvcTester.perform(post(BASE_URL)
                                .with(userJwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            latch.countDown();
            done.await(5, TimeUnit.SECONDS);

            long cardCount = paymentCardRepository.countByUserId(user.getId());
            assertThat(cardCount).isEqualTo(5L);

            executor.shutdown();
        }
    }

    @Nested
    class GetUserCardsTest {

        @Test
        void getUserCardsShouldReturnListOfCardsForAuthenticatedUser() {
            PaymentCardCreateRequest secondCard = new PaymentCardCreateRequest(
                    "5105105105105100", "SECOND HOLDER", YearMonth.now().plusYears(2));
            paymentCardService.create(user.getId(), secondCard);

            mockMvcTester
                    .perform(get(BASE_URL).with(userJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$")
                    .asInstanceOf(LIST)
                    .hasSize(2);
        }

        @Test
        void getUserCardsShouldReturnEmptyListWhenUserHasNoCards() {
            paymentCardRepository.deleteAll();

            mockMvcTester
                    .perform(get(BASE_URL).with(userJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$")
                    .asInstanceOf(LIST)
                    .isEmpty();
        }

        @Test
        void getUserCardsShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            mockMvcTester.perform(get(BASE_URL).with(userJwt())).assertThat().hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void getUserCardsShouldReturnNotFoundWhenUserIsDeleted() {
            userRepository.deleteById(user.getId());

            mockMvcTester.perform(get(BASE_URL).with(userJwt())).assertThat().hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetCardByIdTest {

        @Test
        void getByIdShouldReturnPaymentCardProfileDto() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardView.class)
                    .usingRecursiveComparison()
                    .ignoringFields("updatedAt")
                    .isEqualTo(card);
        }

        @Test
        void getByIdShouldReturnNotFoundWhenCardDoesNotExist() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", UUID.randomUUID()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getByIdShouldReturnNotFoundWhenUserAttemptsToReadAnotherUsersCard() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", card.id()).with(anotherUserJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getByIdShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void getByIdShouldReturnNotFoundWhenUserIsDeleted() {
            userRepository.deleteById(user.getId());

            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteCardTest {

        @Test
        void deleteShouldHardDeleteCardButLeaveUserIntactWhenCardExists() {
            mockMvcTester
                    .perform(delete(BASE_URL + "/{cardId}", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(paymentCardRepository.existsById(card.id())).isFalse();
            assertThat(userRepository.existsById(user.getId())).isTrue();
        }

        @Test
        void deleteShouldReturnNotFoundWhenCardDoesNotExist() {
            mockMvcTester
                    .perform(delete(BASE_URL + "/{cardId}", UUID.randomUUID()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserAttemptsToDeleteAnotherUsersCard() {
            mockMvcTester
                    .perform(delete(BASE_URL + "/{cardId}", card.id()).with(anotherUserJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);

            assertThat(paymentCardRepository.existsById(card.id())).isTrue();
        }

        @Test
        void deleteShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            mockMvcTester
                    .perform(delete(BASE_URL + "/{cardId}", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);

            assertThat(paymentCardRepository.existsById(card.id())).isTrue();
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserIsDeleted() {
            userRepository.deleteById(user.getId());

            mockMvcTester
                    .perform(delete(BASE_URL + "/{cardId}", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeactivateCardTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalse() {
            mockMvcTester
                    .perform(post(BASE_URL + "/{cardId}/deactivate", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.id",
                            id -> id.assertThat().asString().isEqualTo(card.id().toString()))
                    .hasPathSatisfying("$.active", active -> active.assertThat().isEqualTo(false));
        }

        @Test
        void deactivateShouldReturnNotFoundWhenCardDoesNotExist() {
            mockMvcTester
                    .perform(post(BASE_URL + "/{cardId}/deactivate", UUID.randomUUID())
                            .with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void deactivateShouldReturnNotFoundWhenUserAttemptsToDeactivateAnotherUsersCard() {
            mockMvcTester
                    .perform(post(BASE_URL + "/{cardId}/deactivate", card.id()).with(anotherUserJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void deactivateShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            mockMvcTester
                    .perform(post(BASE_URL + "/{cardId}/deactivate", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void deactivateShouldReturnNotFoundWhenUserIsDeleted() {
            userRepository.deleteById(user.getId());

            mockMvcTester
                    .perform(post(BASE_URL + "/{cardId}/deactivate", card.id()).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-security-routes-card.csv", numLinesToSkip = 1)
        void shouldDenyAccessWithoutToken(String method, String route) {
            mockMvcTester
                    .perform(request(HttpMethod.valueOf(method), route))
                    .assertThat()
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}
