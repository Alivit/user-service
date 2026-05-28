package com.minispring.userservice.service.impl;

import ch.qos.logback.classic.Logger;
import com.minispring.userservice.config.JaversConfig;
import com.minispring.userservice.config.JaversConfig.AuditProperties;
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
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.listener.AuditUpdateEvent;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class PaymentCardServiceImplTest {

    @Mock
    private PaymentCardRepository paymentCardRepository;

    @Mock
    private PaymentCardMapper paymentCardMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Spy
    private final Javers javers = JaversBuilder.javers().build();

    @Spy
    private AuditProperties auditProperties = new AuditProperties();

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentCardServiceImpl paymentCardService;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUpGlobal() {
        lenient().when(cacheManager.getCache("user_info")).thenReturn(cache);
        auditProperties = new JaversConfig.AuditProperties();
        auditProperties.setEnabled(true);
    }

    @Nested
    class CreateTest {

        private UUID userId;
        private PaymentCardCreateDto createDto;
        private PaymentCard paymentCard;
        private PaymentCardProfileDto expectedDto;
        private User user;

        @BeforeEach
        public void init() {
            userId = Instancio.create(UUID.class);
            createDto = Instancio.create(PaymentCardCreateDto.class);
            paymentCard = Instancio.create(PaymentCard.class);
            expectedDto = Instancio.create(PaymentCardProfileDto.class);
            user = Instancio.create(User.class);
        }

        @Test
        void createShouldReturnPaymentCardProfileDto() {
            given(userRepository.findUserForUpdateById(userId)).willReturn(Optional.of(user));
            given(paymentCardRepository.countByUserId(userId)).willReturn(4L);
            given(paymentCardMapper.paymentCardCreateDtoToPaymentCard(createDto)).willReturn(paymentCard);
            given(userRepository.getReferenceById(userId)).willReturn(user);
            given(paymentCardRepository.saveAndFlush(paymentCard)).willReturn(paymentCard);
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(paymentCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.create(userId, createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(paymentCardRepository).saveAndFlush(paymentCard);
        }

        @Test
        void createShouldThrowBadRequestExceptionWhenLimitIsExceeded() {
            given(userRepository.findUserForUpdateById(userId)).willReturn(Optional.of(user));
            given(paymentCardRepository.countByUserId(userId)).willReturn(5L);

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(CARD_LIMIT);

            verifyNoInteractions(paymentCardMapper);
            verify(paymentCardRepository, never()).saveAndFlush(any());
        }

        @Test
        void createShouldThrowResourceAlreadyExistsExceptionWhenCardNumberAlreadyExists() {
            given(userRepository.findUserForUpdateById(userId)).willReturn(Optional.of(user));
            given(paymentCardRepository.countByUserId(userId)).willReturn(2L);
            given(paymentCardMapper.paymentCardCreateDtoToPaymentCard(createDto)).willReturn(paymentCard);
            given(userRepository.getReferenceById(userId)).willReturn(user);

            given(paymentCardRepository.saveAndFlush(paymentCard))
                    .willThrow(new DataIntegrityViolationException("Duplicate key value violates unique constraint"));

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(ResourceAlreadyExistsException.class);

            verify(paymentCardMapper, never()).paymentCardToPaymentCardProfileDto(any());
        }

        @Test
        void createShouldThrowResourceNotFoundExceptionWhenUserNotFound() {
            given(userRepository.findUserForUpdateById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            verify(paymentCardRepository, never()).countByUserId(any());
            verifyNoInteractions(paymentCardMapper);
            verify(paymentCardRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class GetByCardIdTest {

        @Test
        void getByIdShouldReturnPaymentCardProfileDto() {
            PaymentCard paymentCard = Instancio.create(PaymentCard.class);
            UUID cardId = paymentCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .create();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(paymentCard));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(paymentCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.getById(cardId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID cardId = Instancio.create(UUID.class);

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.getById(cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    class GetByCardIdWithUserIdTest {

        @Test
        void getByIdShouldReturnPaymentCardProfileDtoWhenUserOwnsCard() {
            UUID userId = Instancio.create(UUID.class);
            User user = Instancio.of(User.class).set(field(User::getId), userId).create();
            PaymentCard paymentCard = Instancio.of(PaymentCard.class).set(field(PaymentCard::getUser), user).create();
            UUID cardId = paymentCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .create();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(paymentCard));
            given(paymentCardMapper.paymentCardToPaymentCardProfileDto(paymentCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.getById(userId, cardId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundExceptionWhenUserDoesNotOwnCard() {
            UUID authUserId = Instancio.create(UUID.class);
            UUID cardOwnerId = Instancio.create(UUID.class);
            User cardOwner = Instancio.of(User.class).set(field(User::getId), cardOwnerId).create();
            PaymentCard paymentCard = Instancio.of(PaymentCard.class).set(field(PaymentCard::getUser), cardOwner).create();
            UUID cardId = paymentCard.getId();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(paymentCard));

            assertThatThrownBy(() -> paymentCardService.getById(authUserId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(paymentCardMapper);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID userId = Instancio.create(UUID.class);
            UUID cardId = Instancio.create(UUID.class);

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.getById(userId, cardId))
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

            given(paymentCardRepository.findAllCardsWithPageable(pageable)).willReturn(cardPage);
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

            given(paymentCardRepository.findAllCardsWithPageable(pageable)).willReturn(emptyCardPage);

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

            given(paymentCardRepository.findAllCardsByUserId(userId)).willReturn(cards);
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

            given(paymentCardRepository.findAllCardsByUserId(userId)).willReturn(List.of());

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

            User user = Instancio.create(User.class);
            existingCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .create();

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
            verify(paymentCardMapper).updateCardFromDto(updateDto, existingCard);
            verify(paymentCardRepository).flush();
            verify(eventPublisher).publishEvent(any(AuditUpdateEvent.class));
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
            verify(paymentCardMapper).updateCardFromDto(updateDto, existingCard);
            verify(paymentCardRepository).flush();
            verify(eventPublisher).publishEvent(any(AuditUpdateEvent.class));
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
            verify(eventPublisher).publishEvent(any(AuditUpdateEvent.class));
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
            verify(eventPublisher).publishEvent(any(AuditUpdateEvent.class));
        }

        @Test
        void updateShouldReturnPaymentCardProfileWithNoChangesDetected(CapturedOutput output) {
            PaymentCardUpdateDto updateDto = Instancio.create(PaymentCardUpdateDto.class);

            setupForUpdateCard(cardStateBefore);

            PaymentCardProfileDto result = paymentCardService.update(cardId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(paymentCardRepository).flush();

            assertThat(output.getOut()).doesNotContain("updated. Changes:");
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
            given(paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(existingCard)).willReturn(expectedDto);
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void deleteShouldPermanentlyDeleteCardWhenCardExists() {
            UUID cardId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(paymentCardRepository.findUserIdByCardId(cardId)).willReturn(Optional.of(userId));
            given(paymentCardRepository.deleteCardById(cardId)).willReturn(1);

            paymentCardService.delete(cardId);

            verify(paymentCardRepository).deleteCardById(cardId);
        }

        @Test
        void deleteShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID cardId = UUID.randomUUID();

            given(paymentCardRepository.findUserIdByCardId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.delete(cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).deleteCardById(any());
        }
    }

    @Nested
    class DeleteWithUserIdTest {

        @Test
        void deleteShouldPermanentlyDeleteCardWhenUserOwnsCard() {
            UUID userId = UUID.randomUUID();
            UUID cardId = UUID.randomUUID();

            given(paymentCardRepository.deleteCardByIdAndUserId(cardId, userId)).willReturn(1);

            paymentCardService.delete(userId, cardId);

            verify(paymentCardRepository).deleteCardByIdAndUserId(cardId, userId);
        }

        @Test
        void deleteShouldThrowResourceNotFoundExceptionWhenUserDoesNotOwnCard() {
            UUID authUserId = UUID.randomUUID();
            UUID cardId = UUID.randomUUID();

            given(paymentCardRepository.deleteCardByIdAndUserId(cardId, authUserId)).willReturn(0);

            assertThatThrownBy(() -> paymentCardService.delete(authUserId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).delete(any(PaymentCard.class));
        }

        @Test
        void deleteShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID userId = UUID.randomUUID();
            UUID cardId = UUID.randomUUID();

            given(paymentCardRepository.deleteCardByIdAndUserId(cardId, userId)).willReturn(0);

            assertThatThrownBy(() -> paymentCardService.delete(userId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).delete(any(PaymentCard.class));
        }
    }

    @Nested
    class DeactivateTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalse() {
            User user = Instancio.create(User.class);
            PaymentCard existingCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .set(field(PaymentCard::getActive), true)
                    .create();
            UUID cardId = existingCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .set(field(PaymentCardProfileDto::active), false)
                    .create();

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(existingCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.deactivate(cardId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(result.active()).isFalse();
            assertThat(existingCard.getActive()).isFalse();
        }

        @Test
        void deactivateShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID cardId = Instancio.create(UUID.class);

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.deactivate(cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).flush();
            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    class DeactivateWithUserIdTest {

        @Test
        void deactivateShouldReturnPaymentCardProfileDtoWithActiveFalseWhenUserOwnsCard() {
            UUID userId = Instancio.create(UUID.class);
            User user = Instancio.of(User.class).set(field(User::getId), userId).create();
            PaymentCard existingCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .set(field(PaymentCard::getActive), true)
                    .create();
            UUID cardId = existingCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .set(field(PaymentCardProfileDto::active), false)
                    .create();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(existingCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.deactivate(userId, cardId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(result.active()).isFalse();
            assertThat(existingCard.getActive()).isFalse();
        }

        @Test
        void deactivateShouldThrowResourceNotFoundExceptionWhenUserDoesNotOwnCard() {
            UUID authUserId = Instancio.create(UUID.class);
            UUID cardOwnerId = Instancio.create(UUID.class);
            User cardOwner = Instancio.of(User.class).set(field(User::getId), cardOwnerId).create();
            PaymentCard existingCard = Instancio.of(PaymentCard.class).set(field(PaymentCard::getUser), cardOwner).create();
            UUID cardId = existingCard.getId();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(existingCard));

            assertThatThrownBy(() -> paymentCardService.deactivate(authUserId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).flush();
            verifyNoInteractions(paymentCardMapper);
        }

        @Test
        void deactivateShouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID userId = Instancio.create(UUID.class);
            UUID cardId = Instancio.create(UUID.class);

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.deactivate(userId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(paymentCardRepository, never()).flush();
            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    class ActivateTest {

        @Test
        void activateShouldReturnPaymentCardProfileDtoWithActiveTrue() {
            User user = Instancio.create(User.class);
            PaymentCard existingCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .set(field(PaymentCard::getActive), false)
                    .create();
            UUID cardId = existingCard.getId();
            PaymentCardProfileDto expectedDto = Instancio.of(PaymentCardProfileDto.class)
                    .set(field(PaymentCardProfileDto::id), cardId)
                    .set(field(PaymentCardProfileDto::active), true)
                    .create();

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(existingCard)).willReturn(expectedDto);

            PaymentCardProfileDto result = paymentCardService.activate(cardId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(result.active()).isTrue();
            assertThat(existingCard.getActive()).isTrue();
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
