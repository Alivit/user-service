package com.minispring.userservice.controller;

import com.minispring.userservice.BaseIntegrationTest;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;

@AutoConfigureMockMvc
public class UserCardPaymentControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentCardService paymentCardService;

    @Autowired
    private JsonMapper jsonMapper;

    private static final String BASE_URL = "/api/v1/cards";
    private User user;
    private PaymentCardProfileDto card;

    @BeforeEach
    public void setUp(TestInfo testInfo) {
        if (testInfo.getTags().contains("skipSetUp")) {
            return;
        }
        UUID authServiceId = Instancio.create(UUID.class);

        PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                .set(field(PaymentCardCreateDto::number), "4000123456789010")
                .create();

        user = Instancio.of(User.class)
                .set(field(User::getId), authServiceId)
                .generate(field(User::getEmail), gen -> gen.text().pattern("#c#c#c#c#c#c#c#c@domain.com"))
                .set(field(User::getActive), true)
                .set(field(User::getCards), new ArrayList<>())
                .ignore(field(User.class, "isNewEntity"))
                .create();

        user = userRepository.saveAndFlush(user);
        card = paymentCardService.create(user.getId(), createDto);
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

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL)
                    .requestAttr("tokenUserId", user.getId())
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
                    .hasPathSatisfying("$.number", number -> assertThat(number).isEqualTo(createDto.number()))
                    .hasPathSatisfying("$.holder", holder -> assertThat(holder).isEqualTo(createDto.holder()))
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true));
        }

        @Test
        void createShouldReturnBadRequestWhenValidationFails() {
            PaymentCardCreateDto invalidDto = Instancio.of(PaymentCardCreateDto.class)
                    .set(field(PaymentCardCreateDto::number), "NotValid")
                    .create();

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL)
                    .requestAttr("tokenUserId", user.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(invalidDto))
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void createShouldReturnNotFoundWhenUserFromTokenDoesNotExist() {
            UUID nonExistentUserId = UUID.randomUUID();

            PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4111111111111111"))
                    .set(field(PaymentCardCreateDto::expirationDate), java.time.YearMonth.now().plusYears(2))
                    .create();

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL)
                    .requestAttr("tokenUserId", nonExistentUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(createDto))
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetUserCardsTest {

        @Test
        void getUserCardsShouldReturnListOfCardsForAuthenticatedUser() {
            PaymentCardCreateDto firstCard = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4444#d#d#d#d#d#d#d#d#d#d#d#d"))
                    .create();
            PaymentCardCreateDto secondCard = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("5555#d#d#d#d#d#d#d#d#d#d#d#d"))
                    .create();

            paymentCardService.create(user.getId(), firstCard);
            paymentCardService.create(user.getId(), secondCard);

            MvcTestResult result = mockMvcTester.get().uri(BASE_URL)
                    .requestAttr("tokenUserId", user.getId())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$", cards -> assertThat(cards).asInstanceOf(LIST).hasSize(3));
        }
    }

    @Nested
    class GetCardByIdTest {

        @Test
        void getByIdShouldReturnPaymentCardProfileDto() {
            MvcTestResult result = mockMvcTester.get().uri(BASE_URL + "/{cardId}", card.id())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(card.id())))
                    .hasPathSatisfying("$.number", number -> assertThat(number).isEqualTo(card.number()))
                    .hasPathSatisfying("$.holder", holder -> assertThat(holder).isEqualTo(card.holder()));
        }

        @Test
        void getByIdShouldReturnNotFoundWhenCardDoesNotExist() {
            UUID invalidId = UUID.randomUUID();

            MvcTestResult result = mockMvcTester.get().uri(BASE_URL + "/{cardId}", invalidId)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeactivateCardTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalse() {
            MvcTestResult result = mockMvcTester.post().uri(BASE_URL + "/{cardId}/deactivate", card.id())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(String.valueOf(card.id())))
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        void deactivateShouldReturnNotFoundWhenCardDoesNotExist() {
            UUID nonExistentCardId = UUID.randomUUID();

            MvcTestResult result = mockMvcTester.post().uri(BASE_URL + "/{cardId}/deactivate", nonExistentCardId)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
