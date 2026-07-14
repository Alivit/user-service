package com.minispring.userservice.service.impl;

import static com.minispring.userservice.exception.ExceptionAnswer.CARD_LIMIT;
import static com.minispring.userservice.exception.ExceptionAnswer.CARD_NOT_FOUND;
import static com.minispring.userservice.exception.ExceptionAnswer.NUMBER_CARD_EXIST;

import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.request.PaymentCardUpdateRequest;
import com.minispring.userservice.dto.response.PaymentCardView;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.PaymentCardMapper;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.service.PaymentCardService;
import com.minispring.userservice.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCardServiceImpl implements PaymentCardService {

    private final PaymentCardRepository paymentCardRepository;
    private final UserService userService;
    private final PaymentCardMapper paymentCardMapper;
    private final CacheManager cacheManager;

    @Override
    @Transactional
    public PaymentCardView create(UUID userId, PaymentCardCreateRequest request) {
        userService.getValidUserEntityForUpdate(userId);

        if (paymentCardRepository.countByUserId(userId) >= 5) {
            throw new BadRequestException(CARD_LIMIT);
        }

        PaymentCard card = paymentCardMapper.toEntity(request);
        card.setUser(userService.getUserReference(userId));

        try {
            PaymentCard savedCard = paymentCardRepository.saveAndFlush(card);
            log.debug(
                    "PaymentCard with id {} created successfully Number: {}", savedCard.getId(), savedCard.getNumber());
            evictUserInfoCache(userId);
            return paymentCardMapper.toView(savedCard);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException(NUMBER_CARD_EXIST);
        }
    }

    @Override
    public PaymentCardView getById(UUID userId, UUID cardId) {
        return paymentCardMapper.toView(getVerifiedUser(userId, cardId));
    }

    @Override
    public PaymentCardView getById(UUID cardId) {
        return paymentCardMapper.toView(getExistsCardWithUserById(cardId));
    }

    @Override
    public Page<PaymentCardView> getAllBy(Pageable pageable) {
        return paymentCardRepository.findAllCardsWithPageable(pageable).map(paymentCardMapper::toView);
    }

    @Override
    public List<PaymentCardView> getAll(UUID userId) {
        userService.getValidUserEntity(userId);

        return paymentCardRepository.findAllCardsByUserId(userId).stream()
                .map(paymentCardMapper::toView)
                .toList();
    }

    @Override
    @Transactional
    public PaymentCardView update(UUID cardId, PaymentCardUpdateRequest request) {
        PaymentCard existingCard = getExistsCardById(cardId);
        evictUserInfoCache(existingCard.getUser().getId());
        paymentCardMapper.update(request, existingCard);
        return paymentCardMapper.toViewWithoutUser(existingCard);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID cardId) {
        userService.getValidUserEntity(userId);

        int deletedRows = paymentCardRepository.deleteCardByIdAndUserId(cardId, userId);
        if (deletedRows == 0) {
            throw new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId));
        }
        evictUserInfoCache(userId);
        log.info("Payment card {} permanently deleted by user {}", cardId, userId);
    }

    @Override
    @Transactional
    public void delete(UUID cardId) {
        UUID userId = paymentCardRepository
                .findUserIdByCardId(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId)));

        paymentCardRepository.deleteCardById(cardId);

        evictUserInfoCache(userId);
        log.info("Payment card {} permanently deleted by ADMIN", cardId);
    }

    @Override
    @Transactional
    public PaymentCardView deactivate(UUID userId, UUID cardId) {
        PaymentCard card = getVerifiedUser(userId, cardId);
        if (card.getActive()) {
            card.setActive(false);
            card.setUpdatedAt(Instant.now());
            log.info("Payment card {} has been deactivated by user {}", cardId, userId);
            evictUserInfoCache(userId);
        }
        return paymentCardMapper.toViewWithoutUser(card);
    }

    @Override
    @Transactional
    public PaymentCardView deactivate(UUID cardId) {
        PaymentCard card = getExistsCardById(cardId);
        if (card.getActive()) {
            card.setActive(false);
            card.setUpdatedAt(Instant.now());
            log.info("Payment card {} has been deactivated by ADMIN", cardId);
            evictUserInfoCache(card.getUser().getId());
        }
        return paymentCardMapper.toViewWithoutUser(card);
    }

    @Override
    @Transactional
    public PaymentCardView activate(UUID cardId) {
        PaymentCard card = getExistsCardById(cardId);
        if (!card.getActive()) {
            card.setActive(true);
            card.setUpdatedAt(Instant.now());
            log.info("Payment card {} has been activated", cardId);
            evictUserInfoCache(card.getUser().getId());
        }
        return paymentCardMapper.toViewWithoutUser(card);
    }

    private PaymentCard getVerifiedUser(UUID userId, UUID cardId) {
        PaymentCard card = getExistsCardWithUserById(cardId);
        if (!card.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId));
        }
        userService.validateUserAllowed(card.getUser());
        return card;
    }

    private PaymentCard getExistsCardWithUserById(UUID cardId) {
        return paymentCardRepository
                .findCardByUserId(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId)));
    }

    private PaymentCard getExistsCardById(UUID cardId) {
        return paymentCardRepository
                .findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId)));
    }

    private void evictUserInfoCache(UUID userId) {
        if (userId != null) {
            Optional.ofNullable(cacheManager.getCache("user_info")).ifPresent(cache -> cache.evict(userId));
        }
    }
}
