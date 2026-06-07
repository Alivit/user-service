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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.instancio.Select.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

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
    private PaymentCardProfileDto card;

    @BeforeEach
    public void setUpUser(TestInfo testInfo) {
        UUID authServiceId = Instancio.create(UUID.class);
        user = Instancio.of(User.class)
                .set(field(User::getId), authServiceId)
                .generate(field(User::getEmail), gen -> gen.text().pattern("#c#c#c#c#c#c#c#c@domain.com"))
                .set(field(User::getActive), true)
                .set(field(User::getCards), new ArrayList<>())
                .ignore(field(User::getVersion))
                .create();

        if (testInfo.getTags().contains("skipInit")) {
            return;
        }

        PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                .set(field(PaymentCardCreateDto::number), "4000123456789010")
                .create();

        user = userRepository.saveAndFlush(user);
        card = paymentCardService.create(user.getId(), createDto);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Nested
    class GetAllCardsTest {

        @BeforeEach
        public void init(TestInfo testInfo){
            if (testInfo.getTags().contains("skipInit")) {
                return;
            }
            PaymentCardCreateDto secondCard = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("5555#d#d#d#d#d#d#d#d#d#d#d#d"))
                    .create();

            paymentCardService.create(user.getId(), secondCard);
        }

        @Test
        void getAllCardsShouldReturnPagedCardsWithDefaultPagination() {
            assertThat(mockMvcTester.get().uri(BASE_URL))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPath("$.content")
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(10));
        }

        @Test
        void getAllCardsShouldRespectCustomPaginationParameters() {
            assertThat(mockMvcTester.get().uri(BASE_URL)
                    .param("page", "0")
                    .param("size", "1"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(2))
                    .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(1))
                    .hasPathSatisfying("$.content", content -> assertThat(content).asInstanceOf(LIST).hasSize(1));
        }

        @Test
        @Tag("skipInit")
        void getAllCardsShouldReturnEmptyPageWhenNoCardsExist() {
            assertThat(mockMvcTester.get().uri(BASE_URL))
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

            assertThat(mockMvcTester.get().uri(BASE_URL + "/user/{userId}", user.getId()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", cards -> assertThat(cards).asInstanceOf(LIST).hasSize(2));
        }

        @Test
        @Tag("skipInit")
        void getUserCardsShouldReturnEmptyListWhenUserHasNoCards() {
            assertThat(mockMvcTester.get().uri(BASE_URL + "/user/{userId}", user.getId()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$", cards -> assertThat(cards).asInstanceOf(LIST).isEmpty());
        }
    }

    @Nested
    class UpdateCardTest {

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedNumber() {
            String testNumber = "5105105105105100";
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::number), testNumber)
                    .create();

            assertThat(mockMvcTester.patch().uri(BASE_URL + "/{cardId}", card.id())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.number", number -> assertThat(number).isEqualTo(testNumber));
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedHolder() {
            String testHolder = "TEST HOLDER";
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::holder), testHolder)
                    .create();

            assertThat(mockMvcTester.patch().uri(BASE_URL + "/{cardId}", card.id())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.holder", holder -> assertThat(holder).isEqualTo(testHolder));
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedExpirationDate() {
            YearMonth testExpirationDate = YearMonth.now().plusYears(3);
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::expirationDate), testExpirationDate)
                    .create();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            String expectedJsonDate = testExpirationDate.format(formatter);

            assertThat(mockMvcTester.patch().uri(BASE_URL + "/{cardId}", card.id())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.expirationDate", date -> assertThat(date).isEqualTo(expectedJsonDate));
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedActiveStatus() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::active), false)
                    .create();

            assertThat(mockMvcTester.patch().uri(BASE_URL + "/{cardId}", card.id())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false));
        }

        @Test
        void updateShouldReturnPaymentCardProfileWithNoChangesDetected() {
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class).create();

            assertThat(mockMvcTester.patch().uri(BASE_URL + "/{cardId}", card.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(card.id().toString()))
                    .hasPathSatisfying("$.number", number -> assertThat(number).isEqualTo(card.number()));
        }

        @Test
        void updateShouldThrowResourceNotFoundException() {
            UUID invalidId = UUID.randomUUID();
            PaymentCardUpdateDto updateDto = Instancio.ofBlank(PaymentCardUpdateDto.class).create();

            assertThat(mockMvcTester.patch().uri(BASE_URL + "/{cardId}", invalidId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(updateDto)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteCardTest {

        @Test
        void deleteShouldHardDeleteCardButLeaveUserIntactWhenCardExists() {
            UUID cardId = card.id();

            MvcTestResult result = mockMvcTester.perform(delete(BASE_URL + "/{cardId}", cardId)
                    .contentType(MediaType.APPLICATION_JSON));

            assertThat(paymentCardRepository.existsById(cardId)).isFalse();
            assertThat(userRepository.existsById(user.getId())).isTrue();
            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        }

        @Test
        void deleteShouldReturnNotFoundWhenCardDoesNotExist() {
            MvcTestResult result = mockMvcTester.perform(delete(BASE_URL + "/{cardId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON));

            assertThat(userRepository.existsById(user.getId())).isTrue();
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeactivateTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalse() {
            PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4444#digits{12}"))
                    .create();

            PaymentCardProfileDto card = paymentCardService.create(user.getId(), createDto);

            assertThat(mockMvcTester.post().uri(BASE_URL + "/{cardId}/deactivate", card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(false))
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(card.id().toString()));
        }

        @Test
        void deactivateShouldThrowResourceNotFoundException() {
            UUID invalidId = UUID.randomUUID();

            assertThat(mockMvcTester.post().uri(BASE_URL + "/{cardId}/deactivate", invalidId))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ActivateTest {

        @Test
        void activateShouldReturnPaymentCardProfileDtoWithActiveTrue() {
            PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4444#digits{12}"))
                    .create();

            PaymentCardProfileDto card = paymentCardService.create(user.getId(), createDto);
            paymentCardService.deactivate(card.id());

            assertThat(mockMvcTester.post().uri(BASE_URL + "/{cardId}/activate", card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.active", active -> assertThat(active).isEqualTo(true))
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(card.id().toString()));
        }

        @Test
        void activateShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID nonExistentCardId = UUID.randomUUID();

            assertThat(mockMvcTester.post().uri(BASE_URL + "/{cardId}/activate", nonExistentCardId))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetCardByIdTest {

        @Test
        void getByIdShouldReturnSingleCard() {
            PaymentCardCreateDto createDto = Instancio.of(PaymentCardCreateDto.class)
                    .generate(field(PaymentCardCreateDto::number), gen -> gen.text().pattern("4444#d#d#d#d#d#d#d#d#d#d#d#d"))
                    .create();

            PaymentCardProfileDto card = paymentCardService.create(user.getId(), createDto);

            assertThat(mockMvcTester.get().uri(BASE_URL + "/{cardId}", card.id()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(card.id().toString()))
                    .hasPathSatisfying("$.holder", holder -> assertThat(holder).isEqualTo(card.holder()))
                    .hasPathSatisfying("$.number", number -> assertThat(number).isEqualTo(card.number()));
        }

        @Test
        void getByIdShouldThrowResourceNotFoundException() {
            UUID nonExistentCardId = UUID.randomUUID();

            assertThat(mockMvcTester.get().uri(BASE_URL + "/{cardId}", nonExistentCardId))
                    .hasStatus(404);
        }
    }
}
