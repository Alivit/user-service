package com.minispring.userservice.controller;

import com.minispring.userservice.dto.request.AdminUserUpdateRequest;
import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.request.UserSearchCriteria;
import com.minispring.userservice.dto.response.PaymentCardView;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.service.PaymentCardService;
import com.minispring.userservice.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final PaymentCardService paymentCardService;
    private final UserService userService;

    @PostMapping("/{userId}/cards")
    public ResponseEntity<PaymentCardView> create(
            @PathVariable UUID userId, @Valid @RequestBody PaymentCardCreateRequest dto) {
        PaymentCardView response = paymentCardService.create(userId, dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{userId}/cards")
    public ResponseEntity<List<PaymentCardView>> getUserCards(@PathVariable UUID userId) {
        return ResponseEntity.ok(paymentCardService.getAll(userId));
    }

    @GetMapping
    public ResponseEntity<Page<UserView>> getAllBy(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @ModelAttribute UserSearchCriteria dto) {
        return ResponseEntity.ok(userService.getAllBy(dto, pageable));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserView> update(@PathVariable UUID userId, @Valid @RequestBody AdminUserUpdateRequest dto) {
        return ResponseEntity.ok(userService.update(userId, dto));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId) {
        userService.delete(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserView> getById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getById(userId));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<UserView> deactivate(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.deactivate(userId));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<UserView> activate(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.activate(userId));
    }
}
