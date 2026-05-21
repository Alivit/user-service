package com.minispring.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "payment_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCard {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String holder;

    @Column(name = "expiration_date", nullable = false)
    @JdbcTypeCode(SqlTypes.DATE)
    private YearMonth expirationDate;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean active = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
