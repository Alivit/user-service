package com.minispring.userservice.repository;

import com.minispring.userservice.model.PaymentCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID> {
}
