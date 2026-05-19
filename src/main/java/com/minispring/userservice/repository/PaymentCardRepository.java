package com.minispring.userservice.repository;

import com.minispring.userservice.model.PaymentCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID> {
    List<PaymentCard> findAllById(UUID id);

    Page<PaymentCard> findAllById(UUID id, Pageable pageable);
}
