package com.minispring.userservice.controller;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.UserService;
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
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@AutoConfigureMockMvc
public class AdminUserControllerIT extends BaseIntegrationTest {

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

    private static final String BASE_URL = "/api/v1/admin/users";
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
    class CreateCardTest {

        @Test
        void createShouldReturnCreatedStatusAndLocationHeader() {
            PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4111111111111111"))
                    .set(field(PaymentCardCreateDto::expirationDate), java.time.YearMonth.now().plusYears(2))
                    .create();

            MvcTestResult result =
                    mockMvcTester.post().uri(BASE_URL + "/{userId}/cards", user.getId())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(createDto))
                            .exchange();

            assertThat(result).hasStatus(org.springframework.http.HttpStatus.CREATED);

            assertThat(result).headers()
                    .extracting(h -> h.getFirst("Location"))
                    .asString()
                    .contains(BASE_URL);

            assertThat(result).bodyJson()
                    .hasPath("$.id")
                    .hasPathSatisfying("$.number", number -> assertThat(number).isEqualTo(createDto.number()))
                    .hasPathSatisfying("$.holder", holder -> assertThat(holder).isEqualTo(createDto.holder()))
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true));
        }

        @Test
        void createShouldReturnBadRequestWhenValidationFails() {
            PaymentCardCreateDto invalidDto = Instancio.of(PaymentCardCreateDto.class)
                    .set(field(PaymentCardCreateDto::number), "NotValid")
                    .create();

            assertThat(mockMvcTester.post().uri(BASE_URL + "/{userId}/cards", user.getId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(invalidDto)))
                    .hasStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        @Test
        void createShouldReturnNotFoundWhenUserDoesNotExist() {
            UUID nonExistentUserId = UUID.randomUUID();
            PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4111111111111111"))
                    .set(field(PaymentCardCreateDto::expirationDate), java.time.YearMonth.now().plusYears(2))
                    .create();

            assertThat(mockMvcTester.post().uri(BASE_URL + "/{userId}/cards", nonExistentUserId)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto)))
                    .hasStatus(org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetAllUsersTest {

        @BeforeEach
        public void init() {
            User exampleUser = Instancio.of(User.class)
                    .set(field(User::getName), "Test")
                    .set(field(User::getSurname), "User")
                    .set(field(User::getCards), new java.util.ArrayList<>())
                    .ignore(field(User::getVersion))
                    .create();
            userRepository.saveAndFlush(exampleUser);
        }

        @Test
        void getAllUsersShouldReturnPagedUsersWithDefaultPagination() {
            MvcTestResult result = mockMvcTester.get().uri(BASE_URL)
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPath("$.content")
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(10));
        }

        @Test
        void getAllUsersShouldFilterByDtoParameters() {
            String targetName = "Test";
            String targetSurname = "User";

            MvcTestResult result = mockMvcTester.get().uri(BASE_URL)
                    .param("name", targetName)
                    .param("surname", targetSurname)
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content", content -> assertThat(content)
                            .asInstanceOf(LIST)
                            .hasSize(1))
                    .hasPathSatisfying("$.content[0].name", name -> assertThat(name).isEqualTo(targetName))
                    .hasPathSatisfying("$.content[0].surname", surname -> assertThat(surname).isEqualTo(targetSurname));
        }

        @Test
        void getAllUsersShouldRespectCustomPaginationParameters() {
            MvcTestResult result = mockMvcTester.get().uri(BASE_URL)
                    .param("page", "0")
                    .param("size", "1")
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(1))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).hasSize(1));
        }
    }

    @Nested
    class UpdateUserTest {

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedName() {
            String testName = "TestName";
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::name), testName)
                    .create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL + "/{userId}", user.getId())
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
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::surname), testSurname)
                    .create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL + "/{userId}", user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(testSurname))
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(user.getName()));
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedBirthDate() {
            LocalDate testBirthDate = LocalDate.now().minusYears(20);
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::birthDate), testBirthDate)
                    .create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL + "/{userId}", user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.birthDate", date -> assertThat(date).isEqualTo(testBirthDate.toString()));
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedActiveStatus() {
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::active), false)
                    .create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL + "/{userId}", user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        void updateWithEmptyDtoShouldReturnUserProfileWithNoChanges() {
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class).create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL + "/{userId}", user.getId())
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
        void updateShouldReturnNotFoundWhenUserDoesNotExist() {
            UUID nonExistentUserId = UUID.randomUUID();
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class).create();

            MvcTestResult result = mockMvcTester.patch().uri(BASE_URL + "/{userId}", nonExistentUserId)
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
            MvcTestResult result = mockMvcTester.perform(delete(BASE_URL  + "/{userId}", user.getId()));

            assertThat(userRepository.existsById(user.getId())).isFalse();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.getId()
            );

            assertThat(deleted).isTrue();
            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserDoesNotExist() {
            MvcTestResult result = mockMvcTester.perform(delete(BASE_URL  + "/{userId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON));

            assertThat(userRepository.existsById(user.getId())).isTrue();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.getId()
            );

            assertThat(deleted).isFalse();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetUserByIdTest {

        @Test
        void getByIdShouldReturnUserProfileDto() {
            MvcTestResult result = mockMvcTester.get().uri(BASE_URL + "/{userId}", user.getId())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(user.getId())))
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(user.getName()))
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(user.getSurname()))
                    .hasPathSatisfying("$.email", email -> assertThat(email).isEqualTo(user.getEmail()));
        }

        @Test
        void getByIdShouldReturnNotFoundWhenUserDoesNotExist() {
            UUID nonExistentUserId = UUID.randomUUID();

            MvcTestResult result = mockMvcTester.get().uri(BASE_URL + "/{userId}", nonExistentUserId)
                    .exchange();

            assertThat(result).hasStatus(404);
        }
    }

    @Nested
    class DeactivateUserTest {

        @Test
        void deactivateShouldReturnUserProfileDtoWithActiveFalse() {
            MvcTestResult result = mockMvcTester.post().uri(BASE_URL + "/{userId}/deactivate", user.getId())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(user.getId())))
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        void deactivateShouldReturnNotFoundWhenUserDoesNotExist() {
            UUID invalidId = UUID.randomUUID();

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL + "/{userId}/deactivate", invalidId)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ActivateUserTest {

        @Test
        void activateShouldReturnUserProfileDtoWithActiveTrue() {
            userService.deactivate(user.getId());

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL + "/{userId}/activate", user.getId())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(user.getId())))
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true));
        }

        @Test
        void activateShouldReturnNotFoundWhenUserDoesNotExist() {
            UUID invalidId = UUID.randomUUID();

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL + "/{userId}/activate", invalidId)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
