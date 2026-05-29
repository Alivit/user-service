package com.minispring.userservice.controller;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

    private static final String BASE_URL = "/api/v1/admin/cards";
    private User user;
    private Jwt adminToken;
    private PaymentCardProfileDto card;

    @BeforeEach
    public void init(TestInfo testInfo) {
        adminToken = createToken("admin", "ADMIN");
        user = userRepository.saveAndFlush(createSecureUser(adminToken.getSubject()));
        if (testInfo.getTags().contains("init")) {
            return;
        }

        PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                .set(field(PaymentCardCreateDto::number), "4000123456789010")
                .create();

        PaymentCardProfileDto created = paymentCardService.create(user.getId(), createDto);
        card = paymentCardService.getById(created.id());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Nested
    class GetAllCardsTest {

        @BeforeEach
        public void initForGetAll(TestInfo testInfo) {
            if (testInfo.getTags().contains("init")) {
                return;
            }
            PaymentCardCreateDto secondCard = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("5555#d#d#d#d#d#d#d#d#d#d#d#d"))
                    .create();

            paymentCardService.create(user.getId(), secondCard);
        }

        @Test
        void getAllCardsShouldReturnPagedCardsWithDefaultPagination() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPath("$.content")
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(10));
        }

        @Test
        void getAllCardsShouldRespectCustomPaginationParameters() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .param("page", "0")
                    .param("size", "1")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(1))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).hasSize(1));
        }

        @Test
        @Tag("init")
        void getAllCardsShouldReturnEmptyPageWhenNoCardsExist() {
            assertThat(mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(0))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class GetUserCardsTest {

        @Test
        void getUserCardsShouldReturnListOfCardsForUser() {
            PaymentCardCreateDto secondCard = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("5555#d#d#d#d#d#d#d#d#d#d#d#d"))
                    .create();

            paymentCardService.create(user.getId(), secondCard);

            assertThat(createRequest())
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", cards -> assertThat(cards).asInstanceOf(LIST).hasSize(2));
        }

        @Test
        @Tag("init")
        void getUserCardsShouldReturnEmptyListWhenUserHasNoCards() {
            assertThat(createRequest())
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", cards -> assertThat(cards).asInstanceOf(LIST).isEmpty());
        }

        private MvcTestResult createRequest() {
            return mockMvcTester.perform(get(BASE_URL + "/user/{userId}", user.getId())
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));
        }
    }

    @Nested
    class UpdateCardTest {

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedNumber() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::number), "5105105105105100")
                    .create();

            assertThat(createRequest(updateDto, card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.number", number -> assertThat(number).isEqualTo(updateDto.number()));
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedHolder() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::holder), "TEST HOLDER")
                    .create();

            assertThat(createRequest(updateDto, card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.holder", holder -> assertThat(holder).isEqualTo(updateDto.holder()));
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedExpirationDate() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::expirationDate), YearMonth.now().plusYears(3))
                    .create();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            String expectedJsonDate = updateDto.expirationDate().format(formatter);

            assertThat(createRequest(updateDto, card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.expirationDate", date -> assertThat(date).isEqualTo(expectedJsonDate));
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedActiveStatus() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::active), false)
                    .create();

            assertThat(createRequest(updateDto, card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        void updateShouldReturnPaymentCardProfileWithNoChangesDetected() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class).create();

            assertThat(createRequest(updateDto, card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("user")
                    .isEqualTo(card);
        }

        @Test
        @Tag("init")
        void updateShouldThrowResourceNotFoundException() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class).create();

            assertThat(createRequest(updateDto, UUID.randomUUID()))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(PaymentCardUpdateDto dto, UUID id) {
            return mockMvcTester.perform(patch(BASE_URL + "/{cardId}", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(dto)));
        }
    }

    @Nested
    class DeleteCardTest {

        @Test
        void deleteShouldHardDeleteCardButLeaveUserIntactWhenCardExists() {
            UUID cardId = card.id();
            MvcTestResult result = createRequest(cardId);

            assertThat(paymentCardRepository.existsById(cardId)).isFalse();
            assertThat(userRepository.existsById(user.getId())).isTrue();
            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        void deleteShouldReturnNotFoundWhenCardDoesNotExist() {
            MvcTestResult result = createRequest(UUID.randomUUID());

            assertThat(userRepository.existsById(user.getId())).isTrue();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(delete(BASE_URL + "/{cardId}", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON));
        }
    }

    @Nested
    class DeactivateTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalse() {
            MvcTestResult result = createRequest(card.id());

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("user", "updatedAt", "active")
                    .isEqualTo(card);

            assertThat(result)
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        @Tag("init")
        void deactivateShouldThrowResourceNotFoundException() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(post(BASE_URL + "/{cardId}/deactivate", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));
        }
    }

    @Nested
    class ActivateTest {

        @Test
        void activateShouldReturnPaymentCardProfileDtoWithActiveTrue() {
            paymentCardService.deactivate(card.id());

            MvcTestResult result = createRequest(card.id());

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("user", "updatedAt")
                    .isEqualTo(card);

            assertThat(result)
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true));
        }

        @Test
        @Tag("init")
        void activateShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(post(BASE_URL + "/{cardId}/activate", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));
        }
    }

    @Nested
    class GetCardByIdTest {

        @Test
        void getByIdShouldReturnSingleCard() {
            assertThat(createRequest(card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("updatedAt")
                    .isEqualTo(card);
        }

        @Test
        @Tag("init")
        void getByIdShouldThrowResourceNotFoundException() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id) {
            return mockMvcTester.perform(get(BASE_URL + "/{cardId}", id)
                    .header("Authorization", "Bearer " + adminToken.getTokenValue()));
        }
    }
}
