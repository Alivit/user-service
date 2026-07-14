package com.minispring.userservice.service.impl;

import static com.minispring.userservice.exception.ExceptionAnswer.CARD_LIMIT;
import static com.minispring.userservice.exception.ExceptionAnswer.CARD_NOT_FOUND;
import static com.minispring.userservice.exception.ExceptionAnswer.NUMBER_CARD_EXIST;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.request.PaymentCardUpdateRequest;
import com.minispring.userservice.dto.response.PaymentCardView;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.PaymentCardMapper;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.service.UserService;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class PaymentCardServiceImplTest {

    @Mock
    private PaymentCardRepository paymentCardRepository;

    @Mock
    private UserService userService;

    @Mock
    private PaymentCardMapper paymentCardMapper;

    @InjectMocks
    private PaymentCardServiceImpl paymentCardService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @BeforeEach
    void setUpGlobal() {
        lenient().when(cacheManager.getCache("user_info")).thenReturn(cache);
    }

    private User createActiveUser(UUID userId) {
        return Instancio.of(User.class)
                .set(field(User::getId), userId)
                .set(field(User::getActive), true)
                .set(field(User::isDeleted), false)
                .create();
    }

    @Nested
    class CreateTest {

        private UUID userId;
        private PaymentCardCreateRequest createDto;
        private PaymentCard paymentCard;
        private PaymentCardView expectedDto;
        private User user;

        @BeforeEach
        void init() {
            userId = UUID.randomUUID();
            createDto = Instancio.create(PaymentCardCreateRequest.class);
            paymentCard = Instancio.create(PaymentCard.class);
            expectedDto = Instancio.create(PaymentCardView.class);
            user = createActiveUser(userId);
        }

        @Test
        void shouldReturnViewAndEvictCache() {
            given(userService.getValidUserEntityForUpdate(userId)).willReturn(user);
            given(paymentCardRepository.countByUserId(userId)).willReturn(4L);
            given(paymentCardMapper.toEntity(createDto)).willReturn(paymentCard);
            given(userService.getUserReference(userId)).willReturn(user);
            given(paymentCardRepository.saveAndFlush(paymentCard)).willReturn(paymentCard);
            given(paymentCardMapper.toView(paymentCard)).willReturn(expectedDto);

            PaymentCardView result = paymentCardService.create(userId, createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);

            then(paymentCardRepository).should().saveAndFlush(paymentCard);
            then(cache).should().evict(userId);
        }

        @Test
        void shouldThrowBadRequestExceptionWhenLimitIsExceeded() {
            given(userService.getValidUserEntityForUpdate(userId)).willReturn(user);
            given(paymentCardRepository.countByUserId(userId)).willReturn(5L);

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(CARD_LIMIT);

            then(paymentCardMapper).shouldHaveNoInteractions();
            then(paymentCardRepository).should(never()).saveAndFlush(any());
        }

        @Test
        void shouldThrowResourceAlreadyExistsException() {
            given(userService.getValidUserEntityForUpdate(userId)).willReturn(user);
            given(paymentCardRepository.countByUserId(userId)).willReturn(2L);
            given(paymentCardMapper.toEntity(createDto)).willReturn(paymentCard);
            given(userService.getUserReference(userId)).willReturn(user);

            given(paymentCardRepository.saveAndFlush(paymentCard))
                    .willThrow(new DataIntegrityViolationException("Duplicate key"));

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(ResourceAlreadyExistsException.class)
                    .hasMessage(NUMBER_CARD_EXIST);
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            given(userService.getValidUserEntityForUpdate(userId))
                    .willThrow(new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(paymentCardRepository).should(never()).countByUserId(any());
            then(paymentCardMapper).shouldHaveNoInteractions();
            then(paymentCardRepository).should(never()).saveAndFlush(any());
            then(cache).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowExceptionWhenUserIsBlockedOrDeleted() {
            given(userService.getValidUserEntityForUpdate(userId))
                    .willThrow(new BadRequestException("Action denied: User is blocked or deleted"));

            assertThatThrownBy(() -> paymentCardService.create(userId, createDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            then(paymentCardRepository).shouldHaveNoInteractions();
            then(paymentCardMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class GetByCardIdTest {

        @Test
        void shouldReturnView() {
            PaymentCard paymentCard = Instancio.create(PaymentCard.class);
            UUID cardId = paymentCard.getId();
            PaymentCardView expectedView = Instancio.create(PaymentCardView.class);

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(paymentCard));
            given(paymentCardMapper.toView(paymentCard)).willReturn(expectedView);

            PaymentCardView result = paymentCardService.getById(cardId);

            assertThat(result).isNotNull().isEqualTo(expectedView);
            then(paymentCardMapper).should().toView(paymentCard);
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID cardId = UUID.randomUUID();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.getById(cardId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(CARD_NOT_FOUND, cardId));

            then(paymentCardMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class GetByCardIdWithUserIdTest {

        @Test
        void shouldReturnView() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            PaymentCard paymentCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .create();
            UUID cardId = paymentCard.getId();
            PaymentCardView expectedView = Instancio.create(PaymentCardView.class);

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(paymentCard));
            given(paymentCardMapper.toView(paymentCard)).willReturn(expectedView);

            PaymentCardView result = paymentCardService.getById(userId, cardId);

            assertThat(result).isNotNull().isEqualTo(expectedView);
            then(userService).should().validateUserAllowed(user);
            then(paymentCardMapper).should().toView(paymentCard);
        }

        @Test
        void shouldThrowBadRequestWhenUserIsBlockedOrDeleted() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            PaymentCard paymentCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .create();
            UUID cardId = paymentCard.getId();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(paymentCard));

            willThrow(new BadRequestException("Action denied: User is blocked or deleted"))
                    .given(userService)
                    .validateUserAllowed(user);

            assertThatThrownBy(() -> paymentCardService.getById(userId, cardId))
                    .isInstanceOf(BadRequestException.class);

            then(paymentCardMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenUserDoesNotOwnCard() {
            UUID authUserId = UUID.randomUUID();
            UUID cardOwnerId = UUID.randomUUID();
            User cardOwner = createActiveUser(cardOwnerId);
            PaymentCard paymentCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), cardOwner)
                    .create();
            UUID cardId = paymentCard.getId();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(paymentCard));

            assertThatThrownBy(() -> paymentCardService.getById(authUserId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(CARD_NOT_FOUND, cardId));

            then(paymentCardMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenCardDoesNotExist() {
            UUID userId = UUID.randomUUID();
            UUID cardId = UUID.randomUUID();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.getById(userId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(CARD_NOT_FOUND, cardId));

            then(paymentCardMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class GetAllByTest {

        @Test
        void shouldReturnPagedViews() {
            Pageable pageable = PageRequest.of(0, 10);
            List<PaymentCard> cards =
                    Instancio.ofList(PaymentCard.class).size(2).create();
            Page<PaymentCard> cardPage = new PageImpl<>(cards, pageable, 2);
            PaymentCardView expectedView = Instancio.create(PaymentCardView.class);

            given(paymentCardRepository.findAllCardsWithPageable(pageable)).willReturn(cardPage);
            given(paymentCardMapper.toView(any(PaymentCard.class))).willReturn(expectedView);

            Page<PaymentCardView> result = paymentCardService.getAllBy(pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactly(expectedView, expectedView);

            then(paymentCardMapper).should().toView(cards.get(0));
            then(paymentCardMapper).should().toView(cards.get(1));
        }

        @Test
        void shouldReturnEmptyPageWhenNoCardsExist() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<PaymentCard> emptyCardPage = Page.empty(pageable);

            given(paymentCardRepository.findAllCardsWithPageable(pageable)).willReturn(emptyCardPage);

            Page<PaymentCardView> result = paymentCardService.getAllBy(pageable);

            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.getTotalElements()).isZero();

            then(paymentCardMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class GetAllTest {

        @Test
        void shouldReturnListOfViews() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            List<PaymentCard> cards =
                    Instancio.ofList(PaymentCard.class).size(3).create();
            PaymentCardView expectedView = Instancio.create(PaymentCardView.class);

            given(userService.getValidUserEntity(userId)).willReturn(user);
            given(paymentCardRepository.findAllCardsByUserId(userId)).willReturn(cards);
            given(paymentCardMapper.toView(any(PaymentCard.class))).willReturn(expectedView);

            List<PaymentCardView> result = paymentCardService.getAll(userId);

            assertThat(result).hasSize(3);
            then(paymentCardMapper).should().toView(cards.getFirst());
        }

        @Test
        void shouldReturnEmptyListWhenUserHasNoCards() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);

            given(userService.getValidUserEntity(userId)).willReturn(user);
            given(paymentCardRepository.findAllCardsByUserId(userId)).willReturn(List.of());

            List<PaymentCardView> result = paymentCardService.getAll(userId);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldThrowExceptionWhenUserIsBlockedOrDeleted() {
            UUID userId = UUID.randomUUID();

            given(userService.getValidUserEntity(userId))
                    .willThrow(new BadRequestException("Action denied: User is blocked or deleted"));

            assertThatThrownBy(() -> paymentCardService.getAll(userId)).isInstanceOf(BadRequestException.class);

            then(paymentCardRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class UpdateTest {

        static Stream<PaymentCardUpdateRequest> provideUpdateRequests() {
            return Stream.of(
                    new PaymentCardUpdateRequest("1234123412341234", null, null),
                    new PaymentCardUpdateRequest(null, "NEW HOLDER", null),
                    new PaymentCardUpdateRequest(null, null, YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest("1234123412341234", "NEW HOLDER", null),
                    new PaymentCardUpdateRequest(
                            "1234123412341234", null, YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest(
                            null, "NEW HOLDER", YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest(
                            "1234123412341234", "NEW HOLDER", YearMonth.now().plusMonths(1)),
                    new PaymentCardUpdateRequest(null, null, null),
                    new PaymentCardUpdateRequest("9999888877776666", "TEST NAME", YearMonth.of(2030, 1)),
                    null);
        }

        @ParameterizedTest
        @MethodSource("provideUpdateRequests")
        void shouldCallMapperAndFlushWhenValidRequest(PaymentCardUpdateRequest request) {
            UUID cardId = UUID.randomUUID();
            PaymentCard existingCard = Instancio.create(PaymentCard.class);
            PaymentCardView expectedView = Instancio.create(PaymentCardView.class);

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.toViewWithoutUser(existingCard)).willReturn(expectedView);

            PaymentCardView result = paymentCardService.update(cardId, request);

            assertThat(result).isEqualTo(expectedView);

            then(paymentCardMapper).should().update(request, existingCard);
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID cardId = UUID.randomUUID();
            PaymentCardUpdateRequest request = Instancio.create(PaymentCardUpdateRequest.class);

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.update(cardId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(CARD_NOT_FOUND, cardId));

            then(paymentCardMapper).should(never()).update(any(), any());
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void shouldPermanentlyDeleteCard() {
            UUID cardId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(paymentCardRepository.findUserIdByCardId(cardId)).willReturn(Optional.of(userId));
            given(paymentCardRepository.deleteCardById(cardId)).willReturn(1);

            paymentCardService.delete(cardId);

            then(paymentCardRepository).should().deleteCardById(cardId);
            then(cache).should().evict(userId);
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID cardId = UUID.randomUUID();

            given(paymentCardRepository.findUserIdByCardId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.delete(cardId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(CARD_NOT_FOUND, cardId));

            then(paymentCardRepository).should(never()).deleteCardById(any());
            then(cache).shouldHaveNoInteractions();
        }
    }

    @Nested
    class DeleteWithUserIdTest {

        @Test
        void shouldPermanentlyDeleteCard() {
            UUID userId = UUID.randomUUID();
            UUID cardId = UUID.randomUUID();
            User user = createActiveUser(userId);

            given(userService.getValidUserEntity(userId)).willReturn(user);
            given(paymentCardRepository.deleteCardByIdAndUserId(cardId, userId)).willReturn(1);

            paymentCardService.delete(userId, cardId);

            then(paymentCardRepository).should().deleteCardByIdAndUserId(cardId, userId);
            then(cache).should().evict(userId);
        }

        @Test
        void shouldThrowExceptionWhenUserIsBlockedOrDeleted() {
            UUID userId = UUID.randomUUID();
            UUID cardId = UUID.randomUUID();

            given(userService.getValidUserEntity(userId))
                    .willThrow(new BadRequestException("Action denied: User is blocked or deleted"));

            assertThatThrownBy(() -> paymentCardService.delete(userId, cardId)).isInstanceOf(BadRequestException.class);

            then(paymentCardRepository).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenUserDoesNotOwnCard() {
            UUID authUserId = UUID.randomUUID();
            UUID cardId = UUID.randomUUID();
            User user = createActiveUser(authUserId);

            given(userService.getValidUserEntity(authUserId)).willReturn(user);
            given(paymentCardRepository.deleteCardByIdAndUserId(cardId, authUserId))
                    .willReturn(0);

            assertThatThrownBy(() -> paymentCardService.delete(authUserId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class DeactivateTest {

        @Test
        void shouldReturnDeactivatedViewAndEvictCache() {
            User user = Instancio.create(User.class);
            PaymentCard card = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .set(field(PaymentCard::getActive), true)
                    .create();
            UUID cardId = card.getId();
            PaymentCardView expectedDto = Instancio.of(PaymentCardView.class)
                    .set(field(PaymentCardView::active), false)
                    .create();

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(card));
            given(paymentCardMapper.toViewWithoutUser(card)).willReturn(expectedDto);

            PaymentCardView result = paymentCardService.deactivate(cardId);

            assertThat(result.active()).isFalse();
            assertThat(card.getActive()).isFalse();
            then(cache).should().evict(user.getId());
        }

        @Test
        void whenCardIsAlreadyInactiveShouldDoNothing() {
            PaymentCard card = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getActive), false)
                    .create();
            UUID cardId = card.getId();
            PaymentCardView expectedDto = Instancio.create(PaymentCardView.class);

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(card));
            given(paymentCardMapper.toViewWithoutUser(card)).willReturn(expectedDto);

            paymentCardService.deactivate(cardId);

            then(cache).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID cardId = UUID.randomUUID();
            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.deactivate(cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(cache).shouldHaveNoInteractions();
        }
    }

    @Nested
    class DeactivateWithUserIdTest {

        @Test
        void shouldReturnDeactivatedViewAndEvictCache() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            PaymentCard existingCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .set(field(PaymentCard::getActive), true)
                    .create();
            UUID cardId = existingCard.getId();
            PaymentCardView expectedDto = Instancio.of(PaymentCardView.class)
                    .set(field(PaymentCardView::active), false)
                    .create();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(existingCard));
            given(paymentCardMapper.toViewWithoutUser(existingCard)).willReturn(expectedDto);

            PaymentCardView result = paymentCardService.deactivate(userId, cardId);

            assertThat(result.active()).isFalse();
            assertThat(existingCard.getActive()).isFalse();
            then(userService).should().validateUserAllowed(user);
            then(cache).should().evict(userId);
        }

        @Test
        void shouldThrowExceptionWhenUserIsBlockedOrDeleted() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            PaymentCard existingCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .create();
            UUID cardId = existingCard.getId();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(existingCard));
            willThrow(new BadRequestException("Action denied: User is blocked or deleted"))
                    .given(userService)
                    .validateUserAllowed(user);

            assertThatThrownBy(() -> paymentCardService.deactivate(userId, cardId))
                    .isInstanceOf(BadRequestException.class);

            verifyNoInteractions(paymentCardMapper);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenUserDoesNotOwnCard() {
            UUID authUserId = Instancio.create(UUID.class);
            UUID cardOwnerId = Instancio.create(UUID.class);
            User cardOwner = createActiveUser(cardOwnerId);
            PaymentCard existingCard = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), cardOwner)
                    .create();
            UUID cardId = existingCard.getId();

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.of(existingCard));

            assertThatThrownBy(() -> paymentCardService.deactivate(authUserId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(cache).shouldHaveNoInteractions();
            verify(paymentCardRepository, never()).flush();
            verifyNoInteractions(paymentCardMapper);
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID userId = Instancio.create(UUID.class);
            UUID cardId = Instancio.create(UUID.class);

            given(paymentCardRepository.findCardByUserId(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.deactivate(userId, cardId))
                    .isInstanceOf(ResourceNotFoundException.class);

            then(cache).shouldHaveNoInteractions();
            verify(paymentCardRepository, never()).flush();
            verifyNoInteractions(paymentCardMapper);
        }
    }

    @Nested
    class ActivateTest {

        @Test
        void shouldReturnActivatedViewAndEvictCache() {
            User user = Instancio.create(User.class);
            PaymentCard card = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getUser), user)
                    .set(field(PaymentCard::getActive), false)
                    .create();
            UUID cardId = card.getId();
            PaymentCardView expectedDto = Instancio.of(PaymentCardView.class)
                    .set(field(PaymentCardView::active), true)
                    .create();

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(card));
            given(paymentCardMapper.toViewWithoutUser(card)).willReturn(expectedDto);

            PaymentCardView result = paymentCardService.activate(cardId);

            assertThat(result.active()).isTrue();
            assertThat(card.getActive()).isTrue();
            then(cache).should().evict(user.getId());
        }

        @Test
        void whenCardIsAlreadyActiveShouldDoNothing() {
            PaymentCard card = Instancio.of(PaymentCard.class)
                    .set(field(PaymentCard::getActive), true)
                    .create();
            UUID cardId = card.getId();
            PaymentCardView expectedDto = Instancio.create(PaymentCardView.class);

            given(paymentCardRepository.findById(cardId)).willReturn(Optional.of(card));
            given(paymentCardMapper.toViewWithoutUser(card)).willReturn(expectedDto);

            paymentCardService.activate(cardId);

            then(cache).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID cardId = UUID.randomUUID();
            given(paymentCardRepository.findById(cardId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCardService.activate(cardId)).isInstanceOf(ResourceNotFoundException.class);

            then(cache).shouldHaveNoInteractions();
        }
    }
}
