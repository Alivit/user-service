package com.minispring.userservice.controller;

import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import com.minispring.userservice.service.PaymentCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/admin/cards")
@RequiredArgsConstructor
public class AdminPaymentCardController {

    private final PaymentCardService paymentCardService;

    @GetMapping
    public ResponseEntity<Page<PaymentCardProfileDto>> getAllCardsBy(@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                                                                     Pageable pageable
    ) {
        return ResponseEntity.ok(paymentCardService.getAllBy(pageable));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentCardProfileDto>> getUserCards(@PathVariable UUID userId) {
        return ResponseEntity.ok(paymentCardService.getAll(userId));
    }

    @PatchMapping("/{cardId}")
    public ResponseEntity<PaymentCardProfileDto> update(@PathVariable UUID cardId,
                                                        @Valid @RequestBody PaymentCardUpdateDto dto
    ) {
        return ResponseEntity.ok(paymentCardService.update(cardId, dto));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable UUID cardId) {
        paymentCardService.delete(cardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cardId}/activate")
    public ResponseEntity<PaymentCardProfileDto> activate(@PathVariable UUID cardId) {
        return ResponseEntity.ok(paymentCardService.activate(cardId));
    }

    @PostMapping("/{cardId}/deactivate")
    public ResponseEntity<PaymentCardProfileDto> deactivate(@PathVariable UUID cardId) {
        return ResponseEntity.ok(paymentCardService.deactivate(cardId));
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<PaymentCardProfileDto> getById(@PathVariable UUID cardId) {
        return ResponseEntity.ok(paymentCardService.getById(cardId));
    }
}
