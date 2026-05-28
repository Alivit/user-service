package com.minispring.userservice.service.impl;

import com.minispring.userservice.config.JaversConfig;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.PaymentCardMapper;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
import com.minispring.userservice.service.listener.AuditUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.CARD_LIMIT;
import static com.minispring.userservice.exception.ExceptionAnswer.CARD_NOT_FOUND;
import static com.minispring.userservice.exception.ExceptionAnswer.NUMBER_CARD_EXIST;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCardServiceImpl implements PaymentCardService {

    private final PaymentCardRepository paymentCardRepository;
    private final UserRepository userRepository;
    private final PaymentCardMapper paymentCardMapper;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;
    private final JaversConfig.AuditProperties auditProperties;

    @Override
    @Transactional
    public PaymentCardProfileDto create(UUID userId, PaymentCardCreateDto paymentCardCreateDto) {
        userRepository.findUserForUpdateById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));

        if (paymentCardRepository.countByUserId(userId) >= 5) {
            throw new BadRequestException(CARD_LIMIT);
        }

        PaymentCard card = paymentCardMapper.paymentCardCreateDtoToPaymentCard(paymentCardCreateDto);
        card.setUser(userRepository.getReferenceById(userId));

        try {
            PaymentCard savedCard = paymentCardRepository.saveAndFlush(card);
            log.debug("PaymentCard with id {} created successfully Number: {}", savedCard.getId(), savedCard.getNumber());
            evictUserInfoCache(userId);
            return paymentCardMapper.paymentCardToPaymentCardProfileDto(savedCard);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException(NUMBER_CARD_EXIST);
        }
    }

    @Override
    public PaymentCardProfileDto getById(UUID userId, UUID cardId) {
        return paymentCardMapper.paymentCardToPaymentCardProfileDto(getVerifiedUser(userId, cardId));
    }

    @Override
    public PaymentCardProfileDto getById(UUID cardId) {
        return paymentCardMapper.paymentCardToPaymentCardProfileDto(getExistsCardWithUserById(cardId));
    }

    @Override
    public Page<PaymentCardProfileDto> getAllBy(Pageable pageable) {
        return paymentCardRepository.findAllCardsWithPageable(pageable)
                .map(paymentCardMapper::paymentCardToPaymentCardProfileDto);
    }

    @Override
    public List<PaymentCardProfileDto> getAll(UUID userId) {
        return paymentCardRepository.findAllCardsByUserId(userId).stream()
                .map(paymentCardMapper::paymentCardToPaymentCardProfileDto).toList();
    }

    @Override
    @Transactional
    public PaymentCardProfileDto update(UUID cardId, PaymentCardUpdateDto paymentCardUpdateDto) {
        PaymentCard existingCard = getExistsCardById(cardId);
        if (!auditProperties.isEnabled()) {
            paymentCardMapper.updateCardFromDto(paymentCardUpdateDto, existingCard);
            return paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(existingCard);
        }
        return processUpdateWithAudit(existingCard, paymentCardUpdateDto);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID cardId) {
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
        UUID userId = paymentCardRepository.findUserIdByCardId(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId)));

        paymentCardRepository.deleteCardById(cardId);

        evictUserInfoCache(userId);
        log.info("Payment card {} permanently deleted by ADMIN", cardId);
    }

    @Override
    @Transactional
    public PaymentCardProfileDto deactivate(UUID userId, UUID cardId) {
        PaymentCard card = getVerifiedUser(userId, cardId);
        if (card.getActive()) {
            card.setActive(false);
            card.setUpdatedAt(Instant.now());
            log.info("Payment card {} has been deactivated by user {}", cardId, userId);
            evictUserInfoCache(userId);
        }
        return paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(card);
    }

    @Override
    @Transactional
    public PaymentCardProfileDto deactivate(UUID cardId) {
        PaymentCard card = getExistsCardById(cardId);
        if (card.getActive()) {
            card.setActive(false);
            card.setUpdatedAt(Instant.now());
            log.info("Payment card {} has been deactivated by ADMIN", cardId);
            evictUserInfoCache(card.getUser().getId());
        }
        return paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(card);
    }

    @Override
    @Transactional
    public PaymentCardProfileDto activate(UUID cardId) {
        PaymentCard card = getExistsCardById(cardId);
        if (!card.getActive()) {
            card.setActive(true);
            card.setUpdatedAt(Instant.now());
            log.info("Payment card {} has been activated", cardId);
            evictUserInfoCache(card.getUser().getId());
        }
        return paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(card);
    }

    private PaymentCard getVerifiedUser(UUID userId, UUID cardId) {
        PaymentCard card = getExistsCardWithUserById(cardId);
        if (!card.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId));
        }
        return card;
    }

    private PaymentCard getExistsCardWithUserById(UUID cardId) {
        return paymentCardRepository.findCardByUserId(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId)));
    }

    private PaymentCard getExistsCardById(UUID cardId) {
        return paymentCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId)));
    }

    private PaymentCardProfileDto processUpdateWithAudit(PaymentCard card, PaymentCardUpdateDto paymentCardUpdateDto) {
        PaymentCardUpdateDto stateBefore = paymentCardMapper.paymentCardToPaymentCardUpdateDto(card);

        paymentCardMapper.updateCardFromDto(paymentCardUpdateDto, card);
        paymentCardRepository.flush();

        PaymentCardUpdateDto stateAfter = paymentCardMapper.paymentCardToPaymentCardUpdateDto(card);

        eventPublisher.publishEvent(new AuditUpdateEvent(card.getId(), stateBefore, stateAfter));
        evictUserInfoCache(card.getUser().getId());
        return paymentCardMapper.CardToPaymentCardProfileDtoWithoutUser(card);
    }

    private void evictUserInfoCache(UUID userId) {
        if (userId != null) {
            Objects.requireNonNull(cacheManager.getCache("user_info")).evict(userId);
        }
    }
}
