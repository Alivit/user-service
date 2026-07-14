package com.minispring.userservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.request.PaymentCardUpdateRequest;
import com.minispring.userservice.dto.response.PaymentCardView;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
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
public class AdminPaymentCardControllerIT extends BaseIntegrationTest {

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

    private final UUID adminId = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/admin/cards";
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

        PaymentCardCreateRequest createDto = Instancio.of(PaymentCardCreateRequest.class)
                .set(field(PaymentCardCreateRequest::number), "4000123456789010")
                .create();

        PaymentCardView created = paymentCardService.create(user.getId(), createDto);
        card = paymentCardService.getById(created.id());
    }

    @AfterEach
    void tearDown() {
        paymentCardRepository.deleteAll();
        userRepository.deleteAll();
    }

    private RequestPostProcessor adminJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(builder -> builder.subject(adminId.toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                        .claim("preferred_username", "admin"));
    }

    private RequestPostProcessor userJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "regular_user"));
    }

    @Nested
    class GetAllCardsTest {

        @BeforeEach
        public void initForGetAll(TestInfo testInfo) {
            if (testInfo.getTags().contains("init")) {
                return;
            }
            PaymentCardCreateRequest secondCard = Instancio.of(PaymentCardCreateRequest.class)
                    .set(field(PaymentCardCreateRequest::number), "5105105105105100")
                    .create();

            paymentCardService.create(user.getId(), secondCard);
        }

        @Test
        void getAllCardsShouldReturnPagedCardsWithDefaultPagination() {
            mockMvcTester
                    .perform(get(BASE_URL).with(adminJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(10));
        }

        @Test
        void getAllCardsShouldRespectCustomPaginationParameters() {
            mockMvcTester
                    .perform(get(BASE_URL).with(adminJwt()).param("page", "0").param("size", "1"))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(1))
                    .hasPathSatisfying(
                            "$.content",
                            content -> assertThat(content).asInstanceOf(LIST).hasSize(1));
        }

        @Test
        void getAllCardsShouldReturnEmptyPageWhenNoCardsExist() {
            paymentCardRepository.deleteAll();
            mockMvcTester
                    .perform(get(BASE_URL).with(adminJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> assertThat(total).isEqualTo(0))
                    .hasPathSatisfying(
                            "$.content",
                            content -> assertThat(content).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class GetCardByIdTest {

        @Test
        void getByIdShouldReturnSingleCard() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", card.id()).with(adminJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardView.class)
                    .usingRecursiveComparison()
                    .ignoringFields("updatedAt")
                    .isEqualTo(card);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundException() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", UUID.randomUUID()).with(adminJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getByIdShouldReturnBadRequestForInvalidUuidFormat() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{cardId}", "invalid-uuid-string").with(adminJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class UpdateCardTest {

        static Stream<PaymentCardUpdateRequest> provideInvalidUpdateRequests() {
            return Stream.of(
                    new PaymentCardUpdateRequest("4000123456789011", null, null),
                    new PaymentCardUpdateRequest("400012345678901A", null, null),
                    new PaymentCardUpdateRequest(null, "A", null),
                    new PaymentCardUpdateRequest(null, "A".repeat(51), null),
                    new PaymentCardUpdateRequest(null, "JOHN D0E", null),
                    new PaymentCardUpdateRequest(null, null, YearMonth.now().minusMonths(1)));
        }

        static Stream<PaymentCardUpdateRequest> provideValidUpdateRequests() {
            return Stream.of(
                    new PaymentCardUpdateRequest(
                            "4242424242424242", "NEW HOLDER", YearMonth.now().plusYears(5)),
                    new PaymentCardUpdateRequest("4242424242424242", "NEW HOLDER", null),
                    new PaymentCardUpdateRequest(
                            "4242424242424242", null, YearMonth.now().plusYears(2)),
                    new PaymentCardUpdateRequest(
                            null, "NEW HOLDER", YearMonth.now().plusYears(2)),
                    new PaymentCardUpdateRequest("4242424242424242", null, null),
                    new PaymentCardUpdateRequest(null, "NEW HOLDER", null),
                    new PaymentCardUpdateRequest(null, null, YearMonth.now().plusYears(2)),
                    new PaymentCardUpdateRequest(null, null, null));
        }

        @ParameterizedTest
        @MethodSource("provideInvalidUpdateRequests")
        void updateShouldReturnBadRequestForInvalidValidation(PaymentCardUpdateRequest invalidRequest) {
            assertThat(mockMvcTester.perform(patch(BASE_URL + "/{cardId}", card.id())
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidRequest))))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @ParameterizedTest
        @MethodSource("provideValidUpdateRequests")
        void updateShouldReturnOkForValidData(PaymentCardUpdateRequest validRequest) {
            MvcTestResult result = mockMvcTester.perform(patch(BASE_URL + "/{cardId}", card.id())
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(validRequest)));

            result.assertThat().hasStatusOk();

            if (validRequest.number() != null) {
                String expectedLast4 = validRequest.number().substring(12);
                result.assertThat().bodyJson().hasPathSatisfying("$.number", n -> n.assertThat()
                        .asString()
                        .endsWith(expectedLast4));
            }
            if (validRequest.holder() != null) {
                result.assertThat().bodyJson().hasPathSatisfying("$.holder", h -> h.assertThat()
                        .asString()
                        .isEqualTo(validRequest.holder()));
            }
            if (validRequest.expirationDate() != null) {
                String expectedDate = validRequest.expirationDate().toString();
                result.assertThat().bodyJson().hasPathSatisfying("$.expirationDate", d -> d.assertThat()
                        .asString()
                        .isEqualTo(expectedDate));
            }
        }

        @Test
        void updateShouldThrowResourceNotFoundException() {
            PaymentCardUpdateRequest validDto = new PaymentCardUpdateRequest(null, "SOME HOLDER", null);

            assertThat(mockMvcTester.perform(patch(BASE_URL + "/{cardId}", UUID.randomUUID())
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validDto))))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteCardTest {

        @Test
        void deleteShouldReturnNoContentAndHardDeleteCard() {
            UUID cardId = card.id();

            assertThat(mockMvcTester.perform(
                            delete(BASE_URL + "/{cardId}", cardId).with(adminJwt())))
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(paymentCardRepository.existsById(cardId)).isFalse();
            assertThat(userRepository.existsById(user.getId())).isTrue();
        }

        @Test
        void deleteShouldReturnNotFoundWhenCardDoesNotExist() {
            assertThat(mockMvcTester.perform(
                            delete(BASE_URL + "/{cardId}", UUID.randomUUID()).with(adminJwt())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeactivateTest {

        @Test
        void deactivateShouldReturnOkAndPaymentCardWithActiveFalse() {
            MvcTestResult result = mockMvcTester.perform(
                    post(BASE_URL + "/{cardId}/deactivate", card.id()).with(adminJwt()));

            result.assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardView.class)
                    .usingRecursiveComparison()
                    .ignoringFields("user", "updatedAt", "active")
                    .isEqualTo(card);

            result.assertThat().bodyJson().hasPathSatisfying("$.active", active -> assertThat(active)
                    .isEqualTo(false));
        }

        @Test
        void deactivateShouldReturnNotFoundWhenCardDoesNotExist() {
            assertThat(mockMvcTester.perform(post(BASE_URL + "/{cardId}/deactivate", UUID.randomUUID())
                            .with(adminJwt())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ActivateTest {

        @Test
        void activateShouldReturnOkAndPaymentCardWithActiveTrue() {
            paymentCardService.deactivate(card.id());

            MvcTestResult result = mockMvcTester.perform(
                    post(BASE_URL + "/{cardId}/activate", card.id()).with(adminJwt()));

            result.assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardView.class)
                    .usingRecursiveComparison()
                    .ignoringFields("user", "updatedAt", "active")
                    .isEqualTo(card);

            result.assertThat().bodyJson().hasPathSatisfying("$.active", active -> assertThat(active)
                    .isEqualTo(true));
        }

        @Test
        void activateShouldReturnNotFoundWhenCardDoesNotExist() {
            assertThat(mockMvcTester.perform(post(BASE_URL + "/{cardId}/activate", UUID.randomUUID())
                            .with(adminJwt())))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes-card.csv", numLinesToSkip = 1)
        void shouldDenyAccessToAllAdminRoutesForRegularUser(String method, String route) {

            String fullUrl = BASE_URL + (route != null ? route : "");

            assertThat(mockMvcTester.perform(
                            request(HttpMethod.valueOf(method), fullUrl).with(userJwt())))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes-card.csv", numLinesToSkip = 1)
        void shouldDenyAccessToAllAdminRoutesWithoutToken(String method, String route) {

            String fullUrl = BASE_URL + (route != null ? route : "");

            assertThat(mockMvcTester.perform(request(HttpMethod.valueOf(method), fullUrl)))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}
