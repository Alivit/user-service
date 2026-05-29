package com.minispring.userservice.controller;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
import com.minispring.userservice.service.UserService;
import com.minispring.userservice.service.impl.AuthGrpcService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
public class AdminUserControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentCardService paymentCardService;

    @Autowired
    private UserService userService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String BASE_URL = "/api/v1/admin/users";
    private UserProfileDto user;
    private Jwt adminToken;

    @BeforeEach
    public void init(TestInfo testInfo) {
        adminToken = createToken("admin", "ADMIN");

        if (testInfo.getTags().contains("init")) {
            return;
        }

        User created = userRepository.saveAndFlush(createSecureUser(adminToken.getSubject()));
        user = userService.getById(created.getId());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Nested
    class CreateCardTest {

        private PaymentCardCreateDto createDto;

        @BeforeEach
        public void initForCreate(){
            createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4111111111111111"))
                    .set(field(PaymentCardCreateDto::expirationDate), java.time.YearMonth.now().plusYears(2))
                    .create();
        }

        @Test
        void createShouldReturnCreatedStatusAndLocationHeader() {
            MvcTestResult result = createRequest(user.id(), createDto);

            assertThat(result)
                    .hasStatus(HttpStatus.CREATED).headers()
                    .hasHeaderSatisfying("Location", values -> {
                        String location = values.getFirst();
                        assertThat(location).contains(BASE_URL);
                        assertThat(location).contains(user.id().toString());
                    });

            assertThat(result).bodyJson()
                    .convertTo(PaymentCardProfileDto.class)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("number", "holder", "expirationDate")
                    .isEqualTo(createDto);

            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true))
                    .hasPath("$.id")
                    .hasPath("$.createdAt")
                    .hasPathSatisfying("$.user.id", id -> assertThat(id).isEqualTo(user.id().toString()));
        }

        @Test
        void createShouldReturnBadRequestWhenValidationFails() {
            PaymentCardCreateDto invalidDto = Instancio.of(PaymentCardCreateDto.class)
                    .set(field(PaymentCardCreateDto::number), "NotValid")
                    .create();

            assertThat(createRequest(user.id(), invalidDto)).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @Tag("init")
        void createShouldReturnNotFoundWhenUserDoesNotExist() {
            assertThat(createRequest(UUID.randomUUID(), createDto)).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void createShouldReturnConflictWhenCardNumberAlreadyExists() {
            paymentCardService.create(user.id(), createDto);

            assertThat(createRequest(user.id(), createDto)).hasStatus(HttpStatus.CONFLICT);
        }

        private MvcTestResult createRequest(UUID id, PaymentCardCreateDto dto) {
            return mockMvcTester.perform(post(BASE_URL + "/{userId}/cards", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(dto)));
        }
    }

    @Nested
    class GetAllUsersTest {

        private User exampleUser;

        @BeforeEach
        public void initForGetAll() {
            exampleUser = userRepository.saveAndFlush(
                    Instancio.of(User.class)
                            .set(field(User::getCards), new java.util.ArrayList<>())
                            .ignore(field(User::getVersion))
                            .create());
        }

        @Test
        void getAllUsersShouldReturnPagedUsersWithDefaultPagination() {
            MvcTestResult result = mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .hasPath("$.content")
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(10));
        }

        @Test
        void getAllUsersShouldFilterByDtoParameters() {
            MvcTestResult result = mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .param("name", exampleUser.getName())
                    .param("surname", exampleUser.getSurname()));

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content", content -> assertThat(content)
                            .asInstanceOf(LIST)
                            .hasSize(1))
                    .hasPathSatisfying("$.content[0].name", name -> assertThat(name).isEqualTo(exampleUser.getName()))
                    .hasPathSatisfying("$.content[0].surname", surname -> assertThat(surname).isEqualTo(exampleUser.getSurname()));
        }

        @Test
        void getAllUsersShouldRespectCustomPaginationParameters() {
            MvcTestResult result = mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .param("page", "0")
                    .param("size", "1"));

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(1))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).hasSize(1));
        }
    }

    @Nested
    class UpdateUserTest {

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedName() {
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::name), "TestName")
                    .create();

            assertThat(createRequest(updateDto, user.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(updateDto.name()))
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(user.surname()));
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedSurname() {
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::surname), "TestSurname")
                    .create();

            assertThat(createRequest(updateDto, user.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.surname", surname -> assertThat(surname).isEqualTo(updateDto.surname()))
                    .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo(user.name()));
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedBirthDate() {
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::birthDate), LocalDate.now().minusYears(20))
                    .create();

            assertThat(createRequest(updateDto, user.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.birthDate", date -> assertThat(date).isEqualTo(updateDto.birthDate().toString()));
        }

        @Test
        void updateWithEmptyDtoShouldReturnUserProfileWithNoChanges() {
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class).create();

            assertThat(createRequest(updateDto, user.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("cards")
                    .isEqualTo(user);
        }

        @Test
        @Tag("init")
        void updateShouldReturnNotFoundWhenUserDoesNotExist() {
            AdminUserUpdateDto updateDto = Instancio.ofBlank(AdminUserUpdateDto.class).create();

            assertThat(createRequest(updateDto, UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(AdminUserUpdateDto dto, UUID id) {
            return mockMvcTester.perform(patch(BASE_URL + "/{userId}", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(dto)));
        }
    }

    @Nested
    class DeleteUserTest {

        @Test
        void deleteShouldReturnNoContentAndPermanentlyDeleteUserWhenUserExists() {
            MvcTestResult result = createRequest(user.id());

            assertThat(userRepository.existsById(user.id())).isFalse();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.id()
            );

            assertThat(deleted).isTrue();
            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserDoesNotExist() {
            MvcTestResult result = createRequest(UUID.randomUUID());

            assertThat(userRepository.existsById(user.id())).isTrue();

            Boolean deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM public.users WHERE id = ?", Boolean.class, user.id()
            );

            assertThat(deleted).isFalse();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(delete(BASE_URL + "/{userId}", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON));
        }
    }

    @Nested
    class GetUserByIdTest {

        @Test
        void getByIdShouldReturnUserProfileDto() {
            assertThat(createRequest(user.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserProfileDto.class)
                    .usingRecursiveComparison()
                    .isEqualTo(user);
        }

        @Test
        @Tag("init")
        void getByIdShouldReturnNotFoundWhenUserDoesNotExist() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(get(BASE_URL + "/{userId}", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));
        }
    }

    @Nested
    class DeactivateUserTest {

        @MockitoBean
        protected AuthGrpcService authGrpcService;

        @Test
        void deactivateShouldReturnUserProfileDtoWithActiveFalse() {
            MvcTestResult result = createRequest(user.id());

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("cards", "updatedAt", "active")
                    .isEqualTo(user);

            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        @Tag("init")
        void deactivateShouldReturnNotFoundWhenUserDoesNotExist() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(post(BASE_URL + "/{userId}/deactivate", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));
        }
    }

    @Nested
    class ActivateUserTest {

        @MockitoBean
        protected AuthGrpcService authGrpcService;

        @Test
        void activateShouldReturnUserProfileDtoWithActiveTrue() {
            userService.deactivate(user.id());

            MvcTestResult result = createRequest(user.id());

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("cards", "updatedAt", "active")
                    .isEqualTo(user);

            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true));
        }

        @Test
        @Tag("init")
        void activateShouldReturnNotFoundWhenUserDoesNotExist() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(post(BASE_URL + "/{userId}/activate", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));
        }
    }
}
