package com.minispring.userservice.controller;

import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.service.PaymentCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/cards")
@RequiredArgsConstructor
public class UserCardPaymentController {

    private final PaymentCardService paymentCardService;

    @PostMapping
    public ResponseEntity<PaymentCardProfileDto> create(@Valid @RequestBody PaymentCardCreateDto dto,
                                                        @AuthenticationPrincipal Jwt jwt
    ) {
        PaymentCardProfileDto response = paymentCardService.create(UUID.fromString(jwt.getSubject()), dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentCardProfileDto>> getUserCards(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(paymentCardService.getAll(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<PaymentCardProfileDto> getById(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID cardId) {
        return ResponseEntity.ok(paymentCardService.getById(UUID.fromString(jwt.getSubject()), cardId));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID cardId) {

        paymentCardService.delete(UUID.fromString(jwt.getSubject()), cardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cardId}/deactivate")
    public ResponseEntity<PaymentCardProfileDto> deactivate(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID cardId) {
        return ResponseEntity.ok(paymentCardService.deactivate(UUID.fromString(jwt.getSubject()), cardId));
    }

}
