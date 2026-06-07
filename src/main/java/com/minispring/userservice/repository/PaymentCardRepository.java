package com.minispring.userservice.repository;

import com.minispring.userservice.model.PaymentCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaymentCard pc WHERE pc.id = :cardId AND pc.user.id = :userId")
    int deleteCardByIdAndUserId(@Param("cardId") UUID cardId, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaymentCard pc WHERE pc.id = :cardId")
    int deleteCardById(@Param("cardId") UUID cardId);

    @Query("SELECT pc.user.id FROM PaymentCard pc WHERE pc.id = :cardId")
    Optional<UUID> findUserIdByCardId(@Param("cardId") UUID cardId);

    @Query("SELECT pc FROM PaymentCard pc JOIN FETCH pc.user WHERE pc.id = :id")
    Optional<PaymentCard> findCardByUserId(@Param("id") UUID id);

    @Query(value = "SELECT pc FROM PaymentCard pc JOIN FETCH pc.user",
            countQuery = "SELECT count(pc) FROM PaymentCard pc")
    Page<PaymentCard> findAllCardsWithPageable(Pageable pageable);

    @Query("SELECT pc FROM PaymentCard pc JOIN FETCH pc.user WHERE pc.user.id = :userId")
    List<PaymentCard> findAllCardsByUserId(@Param("userId") UUID userId);

    Long countByUserId(UUID userId);
}
