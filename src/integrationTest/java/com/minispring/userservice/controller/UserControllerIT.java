package com.minispring.userservice.controller;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

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
    public void setUpUser() {
        UUID authServiceId = Instancio.create(UUID.class);

        user = Instancio.of(User.class)
                .set(field(User::getId), authServiceId)
                .generate(field(User::getEmail), gen -> gen.text().pattern("#c#c#c#c#c#c#c#c@domain.com"))
                .set(field(User::getActive), true)
                .set(field(User::getCards), new ArrayList<>())
                .ignore(field(User::getVersion))
                .create();

        user = userRepository.saveAndFlush(user);
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

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.CREATED);

            assertThat(result).headers()
                    .extracting(h -> h.getFirst("Location"))
                    .asString()
                    .contains(BASE_URL);

            assertThat(result).bodyJson()
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
            MvcTestResult result = mockMvcTester.get().uri(BASE_URL)
                    .requestAttr("tokenUserId", user.getId())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(user.getId())))
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(user.getName()))
                    .hasPathSatisfying("$.email", email -> assertThat(email).isEqualTo(user.getEmail()));
        }

        @Test
        void getByIdShouldReturnNotFoundWhenTokenUserDoesNotExist() {
            UUID invalidId = UUID.randomUUID();

            MvcTestResult result = mockMvcTester.get().uri(BASE_URL)
                    .requestAttr("tokenUserId", invalidId)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UpdateUserTest {

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedName() {
            String testName = "TestName";
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class)
                    .set(field(UserUpdateDto::name), testName)
                    .create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL)
                    .requestAttr("tokenUserId", user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(testName))
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(user.getSurname()));
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedSurname() {
            String testSurname = "TestSurname";
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class)
                    .set(field(UserUpdateDto::surname), testSurname)
                    .create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL)
                    .requestAttr("tokenUserId", user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(testSurname))
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(user.getName()));
        }

        @Test
        void updateWithEmptyDtoShouldReturnUserProfileWithNoChanges() {
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class).create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL)
                    .requestAttr("tokenUserId", user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(user.getId())))
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(user.getName()))
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(user.getSurname()));
        }

        @Test
        void updateShouldReturnNotFoundWhenUserFromTokenDoesNotExist() {
            UUID invalidId = UUID.randomUUID();
            UserUpdateDto updateDto = Instancio.ofBlank(UserUpdateDto.class)
                    .set(field(UserUpdateDto::name), "ValidName")
                    .create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL)
                    .requestAttr("tokenUserId", invalidId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteUserTest {

        @Test
        void deleteShouldReturnNoContentAndPermanentlyDeleteUserWhenUserExists() {
            MvcTestResult result = mockMvcTester.perform(delete(BASE_URL)
                    .requestAttr("tokenUserId", user.getId()));

            assertThat(userRepository.existsById(user.getId())).isFalse();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.getId()
            );

            assertThat(deleted).isTrue();
            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserDoesNotExist() {
            UUID nonExistingUserId = UUID.randomUUID();

            MvcTestResult result = mockMvcTester.perform(delete(BASE_URL)
                            .requestAttr("tokenUserId", nonExistingUserId)
                            .contentType(MediaType.APPLICATION_JSON));

            assertThat(userRepository.existsById(user.getId())).isTrue();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.getId()
            );

            assertThat(deleted).isFalse();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
