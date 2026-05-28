package com.minispring.userservice.repository;

import com.minispring.userservice.model.PaymentCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID> {
    Long countByUserId(UUID userId);

    boolean existsByNumber(String number);

    List<PaymentCard> findAllByUserId(UUID userId);
}
