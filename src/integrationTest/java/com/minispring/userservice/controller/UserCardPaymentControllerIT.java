package com.minispring.userservice.controller;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@AutoConfigureMockMvc
public class UserCardPaymentControllerIT extends BaseIntegrationTest {

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
    private Jwt userToken;
    private PaymentCardProfileDto card;

    @BeforeEach
    public void init(TestInfo testInfo) {
        userToken = createToken("user", "USER");

        if (testInfo.getTags().contains("init")) {
            return;
        }

        PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                .set(field(PaymentCardCreateDto::number), "4000123456789010")
                .create();

        user = userRepository.saveAndFlush(createSecureUser(userToken.getSubject()));
        PaymentCardProfileDto created = paymentCardService.create(user.getId(), createDto);
        card = paymentCardService.getById(created.id());

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

            MvcTestResult result = createRequest(createDto);

            assertThat(result)
                    .hasStatus(HttpStatus.CREATED)
                    .headers()
                    .hasHeaderSatisfying("Location", values -> assertThat(values.getFirst()).contains(BASE_URL));

            assertThat(result).bodyJson()
                    .convertTo(PaymentCardProfileDto.class)
                    .usingRecursiveComparison()
                    .ignoringFields("id", "active", "user", "createdAt", "updatedAt")
                    .isEqualTo(createDto);

            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true));
        }

        @Test
        void createShouldReturnBadRequestWhenValidationFails() {
            PaymentCardCreateDto invalidDto = Instancio.of(PaymentCardCreateDto.class)
                    .set(field(PaymentCardCreateDto::number), "NotValid")
                    .create();

            assertThat(createRequest(invalidDto)).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @Tag("init")
        void createShouldReturnNotFoundWhenUserFromTokenDoesNotExist() {
            PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4111111111111111"))
                    .set(field(PaymentCardCreateDto::expirationDate), java.time.YearMonth.now().plusYears(2))
                    .create();

            assertThat(createRequest(createDto)).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(PaymentCardCreateDto dto){
            return mockMvcTester.perform(post(BASE_URL)
                    .header("Authorization", "Bearer " + userToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(dto)));
        }
    }

    @Nested
    class GetUserCardsTest {

        @Test
        void getUserCardsShouldReturnListOfCardsForAuthenticatedUser() {
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

        private MvcTestResult createRequest(){
            return mockMvcTester.perform(get(BASE_URL)
                    .header("Authorization", "Bearer " + userToken.getTokenValue()));
        }
    }

    @Nested
    class GetCardByIdTest {

        @Test
        void getByIdShouldReturnPaymentCardProfileDto() {
            assertThat(createRequest(card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(PaymentCardProfileDto.class)
                    .usingRecursiveComparison()
                    .isEqualTo(card);
        }

        @Test
        @Tag("init")
        void getByIdShouldReturnNotFoundWhenCardDoesNotExist() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id){
            return mockMvcTester.perform(get(BASE_URL + "/{cardId}", id)
                    .header("Authorization", "Bearer " + userToken.getTokenValue()));
        }
    }

    @Nested
    class DeleteCardTest {

        @Test
        void deleteShouldHardDeleteCardButLeaveUserIntactWhenCardExists() {
            MvcTestResult result = createRequest(card.id());

            assertThat(paymentCardRepository.existsById(card.id())).isFalse();
            assertThat(userRepository.existsById(user.getId())).isTrue();
            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        void deleteShouldReturnNotFoundWhenCardDoesNotExist() {
            MvcTestResult result = createRequest(UUID.randomUUID());

            assertThat(userRepository.existsById(user.getId())).isTrue();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void deleteShouldReturnNotFoundWhenUserIsNotOwnerOfCard() {
            MvcTestResult result = createRequest(UUID.randomUUID());

            assertThat(paymentCardRepository.existsById(card.id())).isTrue();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id){
            return mockMvcTester.perform(delete(BASE_URL + "/{cardId}", id)
                    .header("Authorization", "Bearer " + userToken.getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON));
        }
    }

    @Nested
    class DeactivateCardTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalse() {
            assertThat(createRequest(card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(card.id())))
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        @Tag("init")
        void deactivateShouldReturnNotFoundWhenCardDoesNotExist() {
            assertThat(createRequest(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        }

        private MvcTestResult createRequest(UUID id){
            return mockMvcTester.perform(post(BASE_URL + "/{cardId}/deactivate", id)
                    .header("Authorization", "Bearer " + userToken.getTokenValue()));
        }
    }
}
