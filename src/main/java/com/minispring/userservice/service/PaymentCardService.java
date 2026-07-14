package com.minispring.userservice.service;

import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.request.PaymentCardUpdateRequest;
import com.minispring.userservice.dto.response.PaymentCardView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentCardService {
    PaymentCardView create(UUID userId, PaymentCardCreateRequest request);

    PaymentCardView getById(UUID userId, UUID cardId);

    PaymentCardView getById(UUID cardId);

    Page<PaymentCardView> getAllBy(Pageable pageable);

    List<PaymentCardView> getAll(UUID userId);

    PaymentCardView update(UUID cardId, PaymentCardUpdateRequest request);

    void delete(UUID userId, UUID cardId);

    void delete(UUID cardId);

    PaymentCardView deactivate(UUID userId, UUID cardId);

    PaymentCardView deactivate(UUID cardId);

    PaymentCardView activate(UUID cardId);
}
