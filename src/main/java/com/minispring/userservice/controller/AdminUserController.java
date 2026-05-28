package com.minispring.userservice.controller;

import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.service.PaymentCardService;
import com.minispring.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final PaymentCardService paymentCardService;
    private final UserService userService;

    @PostMapping("/{userId}/cards")
    public ResponseEntity<PaymentCardProfileDto> create(@PathVariable UUID userId,
                                                        @Valid @RequestBody PaymentCardCreateDto dto
    ) {
        PaymentCardProfileDto response = paymentCardService.create(userId, dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<UserProfileDto>> getAllBy(@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                                                         Pageable pageable,
                                                         @ModelAttribute UserParamsDto dto
    ) {
        return ResponseEntity.ok(userService.getAllBy(dto, pageable));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserProfileDto> update(@PathVariable UUID userId,
                                                 @Valid @RequestBody AdminUserUpdateDto dto
    ) {
        return ResponseEntity.ok(userService.update(userId, dto));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileDto> getById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getById(userId));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<UserProfileDto> deactivate(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.deactivate(userId));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<UserProfileDto> activate(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.activate(userId));
    }
}
