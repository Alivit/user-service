package com.minispring.userservice.service;

import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PaymentCardService {
    PaymentCardProfileDto create(UUID userId, PaymentCardCreateDto paymentCardCreateDto);

    PaymentCardProfileDto getById(UUID userId, UUID cardId);

    PaymentCardProfileDto getById(UUID cardId);

    Page<PaymentCardProfileDto> getAllBy(Pageable pageable);

    List<PaymentCardProfileDto> getAll(UUID userId);

    PaymentCardProfileDto update(UUID cardId, PaymentCardUpdateDto paymentCardUpdateDto);

    void delete(UUID userId, UUID cardId);

    void delete(UUID cardId);

    PaymentCardProfileDto deactivate(UUID userId, UUID cardId);

    PaymentCardProfileDto deactivate(UUID cardId);

    PaymentCardProfileDto activate(UUID cardId);
}
