package com.minispring.userservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String surname;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean active = Boolean.TRUE;

    @Transient
    private boolean isNewEntity = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentCard> cards = new ArrayList<>();

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNewEntity = false;
    }
}
