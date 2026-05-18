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

    PaymentCardProfileDto getById(UUID cardId);

    Page<PaymentCardProfileDto> getAllBy(Pageable pageable);

    List<PaymentCardProfileDto> getAll();

    PaymentCardProfileDto update(UUID cardId, PaymentCardUpdateDto paymentCardUpdateDto);

    PaymentCardProfileDto setActive(UUID cardId);
}
