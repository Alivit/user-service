package com.minispring.userservice.controller;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
public class UserControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String BASE_URL = "/api/v1/users";
    private UserProfileDto user;
    private Jwt userToken;

    @BeforeEach
    public void initUser(TestInfo testInfo) {
        userToken = createToken("user", "USER");
        if (testInfo.getTags().contains("initUser")) {
            return;
        }
        User created = userRepository.saveAndFlush(createSecureUser(userToken.getSubject()));
        user = userService.getById(created.getId());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Nested
    class CreateUserTest {

        @Test
        void createShouldReturnCreatedStatusAndLocationHeader() {
            UserCreateDto createDto = Instancio.of(UserCreateDto.class)
                    .generate(field(UserCreateDto::email), gen -> gen.text().pattern("#c#c#c#c#c@domain.com"))
                    .set(field(UserCreateDto::birthDate), LocalDate.now().minusYears(20))
                    .create();

            MvcTestResult result = mockMvcTester.perform(post(BASE_URL)
                    .header("Authorization", "Bearer " + userToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto)));

            assertThat(result)
                    .hasStatus(HttpStatus.CREATED)
                    .headers()
                    .hasHeaderSatisfying("Location", values -> assertThat(values.getFirst()).contains(BASE_URL));

            assertThat(result)
                    .bodyJson()
                    .hasPath("$.id")
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(createDto.name()))
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(createDto.surname()))
                    .hasPathSatisfying("$.email", email -> assertThat(email).isEqualTo(createDto.email()));
        }
    }

    @Nested
    class GetCurrentUserProfileTest {

        @Test
        void getByIdShouldReturnUserProfileOfAuthenticatedUser() {
            assertThat(createRequest())
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserProfileDto.class)
                    .usingRecursiveComparison()
                    .isEqualTo(user);
        }

        @Test
        @Tag("initUser")
        void getByIdShouldReturnNotFoundWhenTokenUserDoesNotExist() {
            assertThat(createRequest()).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest() {
            return mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + userToken.getTokenValue()));
        }
    }

    @Nested
    class UpdateUserTest {

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedName() {
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class)
                    .set(field(UserUpdateDto::name), "TestName")
                    .create();

            assertThat(createRequest(updateDto))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(updateDto.name()))
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(user.surname()));
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedSurname() {
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class)
                    .set(field(UserUpdateDto::surname), "TestSurname")
                    .create();

            assertThat(createRequest(updateDto))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(updateDto.surname()))
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(user.name()));
        }

        @Test
        void updateWithEmptyDtoShouldReturnUserProfileWithNoChanges() {
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class).create();

            assertThat(createRequest(updateDto))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("cards")
                    .isEqualTo(user);
        }

        @Test
        @Tag("initUser")
        void updateShouldReturnNotFoundWhenUserFromTokenDoesNotExist() {
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class)
                    .set(field(UserUpdateDto::name), "ValidName")
                    .create();

            assertThat(createRequest(updateDto)).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UserUpdateDto dto) {
            return mockMvcTester.perform(patch(BASE_URL)
                    .header("Authorization", "Bearer " + userToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(dto)));
        }
    }

    @Nested
    class DeleteUserTest {

        @Test
        void deleteShouldReturnNoContentAndPermanentlyDeleteUserWhenUserExists() {
            MvcTestResult result = createRequest();

            assertThat(userRepository.existsById(user.id())).isFalse();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.id()
            );

            assertThat(deleted).isTrue();
            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        }

        private MvcTestResult createRequest() {
            return mockMvcTester.perform(delete(BASE_URL)
                    .header("Authorization", "Bearer " + userToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON));
        }
    }
}
