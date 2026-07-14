package com.minispring.userservice.controller;

import com.minispring.userservice.dto.request.UserCreateRequest;
import com.minispring.userservice.dto.request.UserUpdateRequest;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserView> create(@Valid @RequestBody UserCreateRequest dto) {
        UserView response = userService.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<UserView> getById(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getById(UUID.fromString(jwt.getSubject())));
    }

    @PatchMapping
    public ResponseEntity<UserView> update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserUpdateRequest dto) {
        return ResponseEntity.ok(userService.update(UUID.fromString(jwt.getSubject()), dto));
    }

    @DeleteMapping()
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        userService.delete(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
