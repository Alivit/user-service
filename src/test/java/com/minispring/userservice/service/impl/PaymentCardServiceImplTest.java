package com.minispring.userservice.service.impl;

import ch.qos.logback.classic.Logger;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.PaymentCardMapper;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.service.UserService;
import org.instancio.Instancio;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.CARD_LIMIT;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class PaymentCardServiceImplTest {

    @Mock
    private PaymentCardRepository paymentCardRepository;

    @Mock
    private PaymentCardMapper paymentCardMapper;

    @Spy
    private final Javers javers = JaversBuilder.javers().build();

    @InjectMocks
    private PaymentCardServiceImpl paymentCardService;

    @Mock
    private UserService userService;

    @Nested
    class CreateTest {

        private UUID userId;
        private PaymentCardCreateDto createDto;
        private PaymentCard paymentCard;
        private PaymentCardProfileDto expectedDto;

        @BeforeEach
        public void init() {
            userId = Instancio.create(UUID.class);
            createDto = Instancio.create(PaymentCardCreateDto.class);
            paymentCard = Instancio.create(PaymentCard.class);
            expectedDto = Instancio.create(PaymentCardProfileDto.class);
        }

        @Test
        void createShouldReturnPaymentCardProfileDto() {
            User userReference = Instancio.of(User.class)
                    .set(field(User::getId), userId)
                    .create();

            given(paymentCardRepository.countByUserId(userId)).willReturn(4L);
            given(paymentCardMapper.paymentCardCreateDtoToPaymentCard(createDto)).willReturn(paymentCard);
            given(paymentCardRepository.existsByNumber(paymentCard.getNumber())).willReturn(false);
            given(userService.getExistingUser(userId)).willReturn(userReference);
            given(paymentCardRepository.saveAndFlush(paymentCard)).willReturn(paymentCard);
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(paymentCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.create(userId, createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(paymentCardRepository).saveAndFlush(paymentCard);
        }

        @Test
        void createShouldThrowBadRequestExceptionWhenLimitIsExceeded() {
            given(paymentCardRepository.countByUserId(userId)).willReturn(5L);

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(CARD_LIMIT);

            verifyNoInteractions(paymentCardMapper, userService);
        }

        @Test
        void createShouldThrowResourceNotFoundException() {
            given(paymentCardRepository.countByUserId(userId)).willReturn(2L);
            given(paymentCardMapper.paymentCardCreateDtoToPaymentCard(createDto)).willReturn(paymentCard);
            given(paymentCardRepository.existsByNumber(paymentCard.getNumber())).willReturn(true);

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(ResourceAlreadyExistsException.class);

            verifyNoInteractions(userService);
            verify(paymentCardRepository, never()).saveAndFlush(any());
        }

        @Test
        void createShouldThrowResourceNotFoundExceptionWhenUserNotFound() {
            given(paymentCardRepository.countByUserId(userId)).willReturn(2L);
            given(paymentCardMapper.paymentCardCreateDtoToPaymentCard(createDto)).willReturn(paymentCard);
            given(paymentCardRepository.existsByNumber(paymentCard.getNumber())).willReturn(false);
            given(userService.getExistingUser(userId)).willThrow(new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class GetByIdTest {

        @Test
        void getByIdShouldReturnPaymentCardProfileDto() {
            PaymentCard paymentCard = Instancio.create(PaymentCard.class);
            UUID cardId = paymentCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .create();

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(paymentCard));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(paymentCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.getById(cardId);

            assertThat(result)
                    .isNotNull()
                    .isEqualTo(expectedDto);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundException() {
            UUID cardId = Instancio.create(UUID.class);

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.getById(cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    class GetAllByTest {

        @Test
        void getAllByShouldReturnPageOfPaymentCardProfileList() {
            Pageable pageable = PageRequest.of(0, 10);
            List<PaymentCard> cards = Instancio.ofList(PaymentCard.class).size(2).create();
            List<PaymentCardProfileDto> expectedList = Instancio.ofList(PaymentCardProfileDto.class).size(2).create();
            Page<PaymentCard> cardPage = new PageImpl<>(cards, pageable, cards.size());

            given(paymentCardRepository.findAll(pageable)).willReturn(cardPage);
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(cards.get(0))).willReturn(expectedList.get(0));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(cards.get(1))).willReturn(expectedList.get(1));

            Page<PaymentCardProfileDto> result = paymentCardService.getAllBy(pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);

            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByShouldReturnEmptyPageWhenNoCardsExist() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<PaymentCard> emptyCardPage = Page.empty(pageable);
            given(paymentCardRepository.findAll(pageable)).willReturn(emptyCardPage);

            Page<PaymentCardProfileDto> result = paymentCardService.getAllBy(pageable);

            assertThat(result).isNotNull().isEmpty();
            assertThat(result.getTotalElements()).isZero();

            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    class GetAllTest {

        @Test
        void getAllShouldReturnListOfPaymentCardProfileList() {
            UUID userId = Instancio.create(UUID.class);
            List<PaymentCard> cards = Instancio.ofList(PaymentCard.class).size(3).create();
            List<PaymentCardProfileDto> expectedList = Instancio.ofList(PaymentCardProfileDto.class).size(3).create();

            given(paymentCardRepository.findAllByUserId(userId)).willReturn(cards);
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(cards.get(0))).willReturn(expectedList.get(0));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(cards.get(1))).willReturn(expectedList.get(1));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(cards.get(2))).willReturn(expectedList.get(2));

            List<PaymentCardProfileDto> result = paymentCardService.getAll(userId);

            assertThat(result)
                    .isNotNull()
                    .hasSize(3)
                    .containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllShouldReturnEmptyListWhenUserHasNoCards() {
            UUID userId = Instancio.create(UUID.class);

            given(paymentCardRepository.findAllByUserId(userId)).willReturn(List.of());

            List<PaymentCardProfileDto> result = paymentCardService.getAll(userId);

            assertThat(result)
                    .isNotNull()
                    .isEmpty();

            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class UpdateTest {

        private UUID cardId;
        private PaymentCard existingCard;
        private PaymentCardProfileDto expectedDto;
        private PaymentCardUpdateDto cardStateBefore;

        @BeforeEach
        void init() {
            Logger logger = (Logger) LoggerFactory.getLogger(PaymentCardServiceImpl.class);
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            cardId = Instancio.create(UUID.class);
            existingCard = Instancio.create(PaymentCard.class);
            expectedDto = Instancio.create(PaymentCardProfileDto.class);
            cardStateBefore = Instancio.of(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::active), true)
                    .create();
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedNumber(CapturedOutput output) {
            String testNumber = "4444555566667777";
            PaymentCardUpdateDto updateDto = Instancio.of(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::number), testNumber)
                    .create();

            setupForUpdateCard(updateDto);

            PaymentCardProfileDto result = paymentCardService.update(cardId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'number' changed:")
                    .contains("-> '" + testNumber + "'");
            verify(paymentCardMapper).updateCardFromDto(updateDto, existingCard);
            verify(paymentCardRepository).flush();
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedHolder(CapturedOutput output) {
            String testHolder = "TEST USER";
            PaymentCardUpdateDto updateDto = Instancio.of(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::holder), testHolder)
                    .create();

            setupForUpdateCard(updateDto);

            PaymentCardProfileDto result = paymentCardService.update(cardId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'holder' changed:")
                    .contains("-> '" + testHolder + "'");
            verify(paymentCardMapper).updateCardFromDto(updateDto, existingCard);
            verify(paymentCardRepository).flush();
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedExpirationDate(CapturedOutput output) {
            YearMonth testExpirationDate = YearMonth.of(2030, 12);
            PaymentCardUpdateDto updateDto = Instancio.of(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::expirationDate), testExpirationDate)
                    .create();

            setupForUpdateCard(updateDto);

            PaymentCardProfileDto result = paymentCardService.update(cardId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(paymentCardRepository).flush();

            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'expirationDate' changed:")
                    .contains("-> '" + testExpirationDate + "'");
        }

        @Test
        void updateShouldReturnPaymentCardProfileDtoWithUpdatedActiveStatus(CapturedOutput output) {
            boolean testActive = false;
            PaymentCardUpdateDto updateDto = Instancio.of(PaymentCardUpdateDto.class)
                    .set(field(PaymentCardUpdateDto::active), testActive)
                    .create();

            setupForUpdateCard(updateDto);

            PaymentCardProfileDto result = paymentCardService.update(cardId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(paymentCardRepository).flush();

            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'active' changed:")
                    .contains("-> '" + testActive + "'");
        }

        @Test
        void updateShouldReturnPaymentCardProfileWithNoChangesDetected(CapturedOutput output) {
            PaymentCardUpdateDto updateDto = Instancio.create(PaymentCardUpdateDto.class);

            setupForUpdateCard(cardStateBefore);

            PaymentCardProfileDto result = paymentCardService.update(cardId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(paymentCardRepository).flush();

            assertThat(output.getOut()).doesNotContain("have changes:");
        }

        @Test
        void updateShouldThrowResourceNotFoundException() {
            PaymentCardUpdateDto updateDto = Instancio.create(PaymentCardUpdateDto.class);
            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.update(cardId, updateDto))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(javers);
            verify(paymentCardRepository, never()).flush();
        }

        private void setupForUpdateCard(PaymentCardUpdateDto cardStateAfter) {
            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.paymentCardToPaymentCardUpdateDto(existingCard))
                    .willReturn(cardStateBefore, cardStateAfter);
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(existingCard)).willReturn(expectedDto);
        }
    }

    @Nested
    class DeactivateTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalse() {
            PaymentCard existingCard = Instancio.of(PaymentCard.class).set(field(PaymentCard::getActive), true).create();
            UUID cardId = existingCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .set(field(PaymentCardProfileDto::active), false)
                    .create();

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(existingCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.deactivate(cardId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(result.active()).isFalse();
            assertThat(existingCard.getActive()).isFalse();

            verify(paymentCardRepository).flush();
        }

        @Test
        void deactivateShouldThrowResourceNotFoundException() {
            UUID cardId = Instancio.create(UUID.class);

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.deactivate(cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).flush();
            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    class ActivateTest {

        @Test
        void activateShouldReturnPaymentCardProfileDtoWithActiveTrue() {
            PaymentCard existingCard = Instancio.of(PaymentCard.class).set(field(PaymentCard::getActive), false).create();
            UUID cardId = existingCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .set(field(PaymentCardProfileDto::active), true)
                    .create();

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(existingCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.activate(cardId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(result.active()).isTrue();
            assertThat(existingCard.getActive()).isTrue();

            verify(paymentCardRepository).flush();
        }

        @Test
        void activateShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID cardId = Instancio.create(UUID.class);
            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.activate(cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).flush();
            verifyNoInteractions(paymentCardMapper);
        }
    }
}
