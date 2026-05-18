package com.minispring.userservice.service.impl;

import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.PaymentCardMapper;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.PaymentCardRepository;
import com.minispring.userservice.repository.UserRepository;
import com.minispring.userservice.service.PaymentCardService;
import com.minispring.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.CARD_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCardServiceImpl implements PaymentCardService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PaymentCardRepository paymentCardRepository;
    private final PaymentCardMapper paymentCardMapper;
    private final Javers javers;

    @Override
    @Transactional
    public PaymentCardProfileDto create(UUID userId, PaymentCardCreateDto paymentCardCreateDto) {
        PaymentCard card = paymentCardMapper.paymentCardCreateDtoToPaymentCard(paymentCardCreateDto);
        userService.getById(userId);
        User user = userRepository.getReferenceById(userId);
        card.setUser(user);
        card.setNumber(maskCardNumber(card.getNumber()));
        PaymentCard savedCard = paymentCardRepository.save(card);
        log.debug("PaymentCard with id {} created successfully Number: {}", savedCard.getId(), savedCard.getNumber());
        return paymentCardMapper.paymentCardToPaymentCardProfileDto(savedCard);
    }

    @Override
    public PaymentCardProfileDto getById(UUID cardId) {
        PaymentCard foundedCard = getExistingCard(cardId);
        log.debug("Payment card with id {} has been found", cardId);
        return paymentCardMapper.paymentCardToPaymentCardProfileDto(foundedCard);
    }

    @Override
    public Page<PaymentCardProfileDto> getAllBy(Pageable pageable) {
        return paymentCardRepository.findAll(pageable)
                .map(paymentCardMapper::paymentCardToPaymentCardProfileDto);
    }

    @Override
    public List<PaymentCardProfileDto> getAll() {
        return paymentCardRepository.findAll().stream()
                .map(paymentCardMapper::paymentCardToPaymentCardProfileDto).toList();
    }

    @Override
    @Transactional
    public PaymentCardProfileDto update(UUID cardId, PaymentCardUpdateDto paymentCardUpdateDto) {
        PaymentCard existingCard = getExistingCard(cardId);
        PaymentCardUpdateDto cardStateBefore = paymentCardMapper.paymentCardToPaymentCardUpdateDto(existingCard);
        paymentCardMapper.updateCardFromDto(paymentCardUpdateDto, existingCard);
        PaymentCardUpdateDto cardStateAfter = paymentCardMapper.paymentCardToPaymentCardUpdateDto(existingCard);
        Diff diff = javers.compare(cardStateBefore, cardStateAfter);

        if (diff.hasChanges()) {
            log.debug("Payment card {} have changes: {}", cardId, diff.prettyPrint());
        }
        return paymentCardMapper.paymentCardToPaymentCardProfileDto(existingCard);
    }

    @Override
    @Transactional
    public PaymentCardProfileDto setActive(UUID cardId) {
        PaymentCard existingCard = getExistingCard(cardId);
        existingCard.setActive(!existingCard.getActive());
        log.debug("Payment card ID: {}. Active status changed to: {}", cardId, existingCard.getActive());
        return paymentCardMapper.paymentCardToPaymentCardProfileDto(existingCard);
    }

    private String maskCardNumber(String cardNumber) {
        return cardNumber.substring(0, 6) + "******" + cardNumber.substring(cardNumber.length() - 4);
    }

    private PaymentCard getExistingCard(UUID cardId) {
        return paymentCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CARD_NOT_FOUND, cardId)));
    }
}
