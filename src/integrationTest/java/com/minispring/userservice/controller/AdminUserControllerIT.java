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
import com.minispring.userservice.dto.request.AdminUserUpdateRequest;
import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
public class AdminUserControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentCardRepository paymentCardRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String BASE_URL = "/api/v1/admin/users";
    private final UUID adminId = UUID.randomUUID();
    private User user;

    @BeforeEach
    public void setup() {
        user = Instancio.of(User.class)
                .ignore(field(User::getVersion))
                .ignore(field(User::getCards))
                .set(field(User::getActive), true)
                .set(field(User::isDeleted), false)
                .create();
        user.setId(UUID.randomUUID());
        user = userRepository.saveAndFlush(user);
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
    class CreateCardForUserTest {

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
                    .perform(post(BASE_URL + "/{userId}/cards", user.getId())
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void createShouldReturnCreatedStatusAndLocationHeader() {
            PaymentCardCreateRequest createDto = new PaymentCardCreateRequest(
                    "4242424242424242", "ADMIN HOLDER", YearMonth.now().plusYears(2));

            MvcTestResult result = mockMvcTester.perform(post(BASE_URL + "/{userId}/cards", user.getId())
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto)));

            result.assertThat().hasStatus(HttpStatus.CREATED).headers().hasHeaderSatisfying("Location", values -> {
                String location = values.getFirst();
                assertThat(location).contains(BASE_URL);
                assertThat(location).contains(user.getId().toString());
            });

            result.assertThat()
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> active.assertThat().isEqualTo(true))
                    .hasPathSatisfying("$.user.id", id -> id.assertThat()
                            .asString()
                            .isEqualTo(user.getId().toString()));
        }

        @Test
        void createShouldReturnNotFoundWhenUserDoesNotExist() {
            PaymentCardCreateRequest validDto = new PaymentCardCreateRequest(
                    "4242424242424242", "ADMIN HOLDER", YearMonth.now().plusYears(2));

            mockMvcTester
                    .perform(post(BASE_URL + "/{userId}/cards", UUID.randomUUID())
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldRespectCardLimitUnderConcurrentAdminRequests() throws Exception {
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
                        mockMvcTester.perform(post(BASE_URL + "/{userId}/cards", user.getId())
                                .with(adminJwt())
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
    class GetAllUsersTest {

        private User exampleUser;

        @BeforeEach
        public void initForGetAll() {
            exampleUser = userRepository.saveAndFlush(Instancio.of(User.class)
                    .ignore(field(User::getVersion))
                    .ignore(field(User::getCards))
                    .create());
        }

        @Test
        void getAllUsersShouldReturnPagedUsersWithDefaultPagination() {
            mockMvcTester
                    .perform(get(BASE_URL).with(adminJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> total.assertThat().isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> size.assertThat().isEqualTo(10));
        }

        @Test
        void getAllUsersShouldFilterByDtoParameters() {
            mockMvcTester
                    .perform(get(BASE_URL)
                            .with(adminJwt())
                            .param("name", exampleUser.getName())
                            .param("surname", exampleUser.getSurname()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> total.assertThat().isEqualTo(1))
                    .extractingPath("$.content")
                    .asInstanceOf(LIST)
                    .hasSize(1);
        }

        @Test
        void getAllUsersShouldRespectCustomPaginationParameters() {
            mockMvcTester
                    .perform(get(BASE_URL).with(adminJwt()).param("page", "0").param("size", "1"))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying(
                            "$.page.totalElements", total -> total.assertThat().isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> size.assertThat().isEqualTo(1))
                    .extractingPath("$.content")
                    .asInstanceOf(LIST)
                    .hasSize(1);
        }
    }

    @Nested
    class UpdateUserTest {

        static Stream<AdminUserUpdateRequest> provideInvalidUpdateRequests() {
            return Stream.of(
                    new AdminUserUpdateRequest("A", null, null),
                    new AdminUserUpdateRequest(null, "B", null),
                    new AdminUserUpdateRequest("A".repeat(101), null, null),
                    new AdminUserUpdateRequest(null, "B".repeat(101), null),
                    new AdminUserUpdateRequest("Admin123", null, null),
                    new AdminUserUpdateRequest(null, "User_!", null),
                    new AdminUserUpdateRequest(null, null, LocalDate.now().minusYears(5)),
                    new AdminUserUpdateRequest(null, null, LocalDate.now().minusYears(121)),
                    new AdminUserUpdateRequest(null, null, LocalDate.now().plusDays(1)));
        }

        static Stream<AdminUserUpdateRequest> provideValidUpdateRequests() {
            return Stream.of(
                    new AdminUserUpdateRequest(
                            "Alexander", "Great", LocalDate.now().minusYears(30)),
                    new AdminUserUpdateRequest("Maxim", null, null),
                    new AdminUserUpdateRequest(null, "Ivanov", null),
                    new AdminUserUpdateRequest(null, null, LocalDate.now().minusYears(25)),
                    new AdminUserUpdateRequest("Bo", "Li", LocalDate.now().minusYears(6)),
                    new AdminUserUpdateRequest(null, null, LocalDate.now().minusYears(120)),
                    new AdminUserUpdateRequest(null, null, null));
        }

        @ParameterizedTest
        @MethodSource("provideInvalidUpdateRequests")
        void updateShouldReturnBadRequestForInvalidData(AdminUserUpdateRequest invalidDto) {
            mockMvcTester
                    .perform(patch(BASE_URL + "/{userId}", user.getId())
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @ParameterizedTest
        @MethodSource("provideValidUpdateRequests")
        void updateShouldReturnOkAndUpdatedFields(AdminUserUpdateRequest updateDto) {
            MvcTestResult result = mockMvcTester.perform(patch(BASE_URL + "/{userId}", user.getId())
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto)));

            result.assertThat().hasStatusOk();

            if (updateDto.name() != null) {
                result.assertThat().bodyJson().hasPathSatisfying("$.name", n -> n.assertThat()
                        .asString()
                        .isEqualTo(updateDto.name()));
            } else {
                result.assertThat().bodyJson().hasPathSatisfying("$.name", n -> n.assertThat()
                        .asString()
                        .isEqualTo(user.getName()));
            }

            if (updateDto.surname() != null) {
                result.assertThat().bodyJson().hasPathSatisfying("$.surname", s -> s.assertThat()
                        .asString()
                        .isEqualTo(updateDto.surname()));
            } else {
                result.assertThat().bodyJson().hasPathSatisfying("$.surname", s -> s.assertThat()
                        .asString()
                        .isEqualTo(user.getSurname()));
            }

            if (updateDto.birthDate() != null) {
                String expectedDate = updateDto.birthDate().toString();
                result.assertThat().bodyJson().hasPathSatisfying("$.birthDate", d -> d.assertThat()
                        .asString()
                        .isEqualTo(expectedDate));
            }
        }

        @Test
        void updateShouldReturnNotFoundWhenUserDoesNotExist() {
            AdminUserUpdateRequest validDto = new AdminUserUpdateRequest("ValidName", null, null);

            mockMvcTester
                    .perform(patch(BASE_URL + "/{userId}", UUID.randomUUID())
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteUserTest {

        @Test
        void deleteShouldReturnNoContentAndPermanentlyDeleteUserWhenUserExists() {
            mockMvcTester
                    .perform(delete(BASE_URL + "/{userId}", user.getId()).with(adminJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(userRepository.existsById(user.getId())).isFalse();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.getId());
            assertThat(deleted).isTrue();
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserDoesNotExist() {
            mockMvcTester
                    .perform(delete(BASE_URL + "/{userId}", UUID.randomUUID()).with(adminJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetUserByIdTest {

        @Test
        void getByIdShouldReturnUserProfileDto() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{userId}", user.getId()).with(adminJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserView.class)
                    .usingRecursiveComparison()
                    .ignoringFields("cards", "createdAt", "updatedAt")
                    .isEqualTo(user);
        }

        @Test
        void getByIdShouldReturnNotFoundWhenUserDoesNotExist() {
            mockMvcTester
                    .perform(get(BASE_URL + "/{userId}", UUID.randomUUID()).with(adminJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeactivateUserTest {

        @Test
        void shouldReturnUserProfileDtoWithActiveFalse() {
            MvcTestResult result = mockMvcTester.perform(
                    post(BASE_URL + "/{userId}/deactivate", user.getId()).with(adminJwt()));

            result.assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserView.class)
                    .usingRecursiveComparison()
                    .ignoringFields("cards", "createdAt", "updatedAt", "active")
                    .isEqualTo(user);

            result.assertThat().bodyJson().hasPathSatisfying("$.active", active -> active.assertThat()
                    .isEqualTo(false));
        }

        @Test
        void shouldReturnNotFoundWhenUserDoesNotExist() {
            mockMvcTester
                    .perform(post(BASE_URL + "/{userId}/deactivate", UUID.randomUUID())
                            .with(adminJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ActivateUserTest {

        @Test
        void shouldReturnUserProfileDtoWithActiveTrue() {
            userService.deactivate(user.getId());

            MvcTestResult result = mockMvcTester.perform(
                    post(BASE_URL + "/{userId}/activate", user.getId()).with(adminJwt()));

            result.assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserView.class)
                    .usingRecursiveComparison()
                    .ignoringFields("cards", "createdAt", "updatedAt", "active")
                    .isEqualTo(user);

            result.assertThat().bodyJson().hasPathSatisfying("$.active", active -> active.assertThat()
                    .isEqualTo(true));
        }

        @Test
        void shouldReturnNotFoundWhenUserDoesNotExist() {
            mockMvcTester
                    .perform(post(BASE_URL + "/{userId}/activate", UUID.randomUUID())
                            .with(adminJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessWithoutToken(String method, String route) {
            mockMvcTester
                    .perform(request(HttpMethod.valueOf(method), route))
                    .assertThat()
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/admin/admin-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessForRegularUser(String method, String route) {
            mockMvcTester
                    .perform(request(HttpMethod.valueOf(method), route).with(userJwt()))
                    .assertThat()
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
