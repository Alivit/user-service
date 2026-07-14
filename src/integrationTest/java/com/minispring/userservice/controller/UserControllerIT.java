package com.minispring.userservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.request.UserCreateRequest;
import com.minispring.userservice.dto.request.UserUpdateRequest;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
public class UserControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String BASE_URL = "/api/v1/users";
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
        userRepository.deleteAll();
    }

    private RequestPostProcessor userJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(user.getId().toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", user.getEmail()));
    }

    private RequestPostProcessor userJwt(UUID customId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(customId.toString())
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "unknown_user"));
    }

    private RequestPostProcessor gatewayJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
                .jwt(builder -> builder.subject("api-gateway-client"));
    }

    @Nested
    class CreateUserTest {

        static Stream<UserCreateRequest> provideInvalidCreateRequests() {
            return Stream.of(
                    Instancio.of(UserCreateRequest.class)
                            .set(field(UserCreateRequest::email), "not-an-email")
                            .create(),
                    Instancio.of(UserCreateRequest.class)
                            .set(field(UserCreateRequest::email), "")
                            .create(),
                    Instancio.of(UserCreateRequest.class)
                            .set(field(UserCreateRequest::name), " ")
                            .create(),
                    Instancio.of(UserCreateRequest.class)
                            .set(field(UserCreateRequest::surname), "")
                            .create(),
                    Instancio.of(UserCreateRequest.class)
                            .set(
                                    field(UserCreateRequest::birthDate),
                                    LocalDate.now().plusDays(1))
                            .create());
        }

        @ParameterizedTest
        @MethodSource("provideInvalidCreateRequests")
        void createShouldReturnBadRequestWhenValidationFails(UserCreateRequest invalidDto) {
            mockMvcTester
                    .perform(post(BASE_URL)
                            .with(gatewayJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void createShouldReturnCreatedStatusAndLocationHeader() {
            UserCreateRequest createDto = Instancio.of(UserCreateRequest.class)
                    .generate(field(UserCreateRequest::email), gen -> gen.text().pattern("#c#c#c#c#c@domain.com"))
                    .set(field(UserCreateRequest::birthDate), LocalDate.now().minusYears(20))
                    .create();

            MvcTestResult result = mockMvcTester.perform(post(BASE_URL)
                    .with(gatewayJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto)));

            result.assertThat()
                    .hasStatus(HttpStatus.CREATED)
                    .headers()
                    .hasHeaderSatisfying(
                            "Location", values -> assertThat(values.getFirst()).contains(BASE_URL));

            result.assertThat()
                    .bodyJson()
                    .hasPath("$.id")
                    .hasPathSatisfying(
                            "$.name", name -> name.assertThat().asString().isEqualTo(createDto.name()))
                    .hasPathSatisfying(
                            "$.surname",
                            surname -> surname.assertThat().asString().isEqualTo(createDto.surname()))
                    .hasPathSatisfying(
                            "$.email", email -> email.assertThat().asString().isEqualTo(createDto.email()));
        }

        @Test
        void createShouldReturnForbiddenWhenCalledByNormalUser() {
            UserCreateRequest validDto = Instancio.of(UserCreateRequest.class)
                    .generate(field(UserCreateRequest::email), gen -> gen.text().pattern("#c#c#c#c#c@domain.com"))
                    .set(field(UserCreateRequest::birthDate), LocalDate.now().minusYears(20))
                    .create();

            mockMvcTester
                    .perform(post(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        void createShouldReturnUnauthorizedWhenCalledWithoutAnyToken() {
            UserCreateRequest validDto = Instancio.of(UserCreateRequest.class)
                    .generate(field(UserCreateRequest::email), gen -> gen.text().pattern("#c#c#c#c#c@domain.com"))
                    .set(field(UserCreateRequest::birthDate), LocalDate.now().minusYears(20))
                    .create();

            mockMvcTester
                    .perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class GetCurrentUserProfileTest {

        @Test
        void getShouldReturnUserProfileOfAuthenticatedUser() {
            mockMvcTester
                    .perform(get(BASE_URL).with(userJwt()))
                    .assertThat()
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> id.assertThat()
                            .asString()
                            .isEqualTo(user.getId().toString()))
                    .hasPathSatisfying(
                            "$.email", email -> email.assertThat().asString().isEqualTo(user.getEmail()));
        }

        @Test
        void getShouldReturnNotFoundWhenTokenUserDoesNotExist() {
            mockMvcTester
                    .perform(get(BASE_URL).with(userJwt(UUID.randomUUID())))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void getShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            mockMvcTester.perform(get(BASE_URL).with(userJwt())).assertThat().hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void getShouldReturnNotFoundWhenUserIsDeleted() {
            userRepository.deleteById(user.getId());

            mockMvcTester.perform(get(BASE_URL).with(userJwt())).assertThat().hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UpdateUserTest {

        static Stream<UserUpdateRequest> provideInvalidUpdateRequests() {
            return Stream.of(
                    new UserUpdateRequest("A", null),
                    new UserUpdateRequest(null, "B"),
                    new UserUpdateRequest("A".repeat(101), null),
                    new UserUpdateRequest(null, "B".repeat(101)),
                    new UserUpdateRequest("Name123", null),
                    new UserUpdateRequest(null, "Surname456"),
                    new UserUpdateRequest("John!", null),
                    new UserUpdateRequest(null, "Doe@"));
        }

        static Stream<UserUpdateRequest> provideValidUpdateRequests() {
            return Stream.of(
                    new UserUpdateRequest("Alexander", "Great"),
                    new UserUpdateRequest("Maxim", null),
                    new UserUpdateRequest(null, "Ivanov"),
                    new UserUpdateRequest("Bo", "Li"),
                    new UserUpdateRequest("Jean-Paul", "Smith-Rowe"),
                    new UserUpdateRequest(null, null));
        }

        @ParameterizedTest
        @MethodSource("provideInvalidUpdateRequests")
        void updateShouldReturnBadRequestForInvalidValidation(UserUpdateRequest invalidDto) {
            mockMvcTester
                    .perform(patch(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @ParameterizedTest
        @MethodSource("provideValidUpdateRequests")
        void updateShouldReturnOkAndUpdatedFields(UserUpdateRequest updateDto) {
            MvcTestResult result = mockMvcTester.perform(patch(BASE_URL)
                    .with(userJwt())
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
        }

        @Test
        void updateShouldReturnNotFoundWhenTokenUserDoesNotExist() {
            UserUpdateRequest updateDto = new UserUpdateRequest("ValidName", null);

            mockMvcTester
                    .perform(patch(BASE_URL)
                            .with(userJwt(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(updateDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void updateShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            UserUpdateRequest updateDto = new UserUpdateRequest("NewName", null);

            mockMvcTester
                    .perform(patch(BASE_URL)
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(updateDto)))
                    .assertThat()
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class DeleteUserTest {

        @Test
        void deleteShouldReturnNoContentAndSoftDeleteUser() {
            mockMvcTester.perform(delete(BASE_URL).with(userJwt())).assertThat().hasStatus(HttpStatus.NO_CONTENT);

            assertThat(userRepository.existsById(user.getId())).isFalse();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.getId());
            assertThat(deleted).isTrue();
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserDoesNotExist() {
            mockMvcTester
                    .perform(delete(BASE_URL).with(userJwt(UUID.randomUUID())))
                    .assertThat()
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void deleteShouldReturnBadRequestWhenUserIsBlocked() {
            user.setActive(false);
            userRepository.saveAndFlush(user);

            mockMvcTester.perform(delete(BASE_URL).with(userJwt())).assertThat().hasStatus(HttpStatus.BAD_REQUEST);

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.getId());
            assertThat(deleted).isFalse();
        }
    }

    @Nested
    class SecurityAuthorizationTests {

        @ParameterizedTest
        @CsvFileSource(resources = "/testdata/user/user-security-routes.csv", numLinesToSkip = 1)
        void shouldDenyAccessWithoutToken(String method, String route) {
            mockMvcTester
                    .perform(request(HttpMethod.valueOf(method), route))
                    .assertThat()
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}
