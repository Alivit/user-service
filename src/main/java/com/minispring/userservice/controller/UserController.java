package com.minispring.userservice.controller;

import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserProfileDto> create(@Valid @RequestBody UserCreateDto dto) {
        UserProfileDto response = userService.create(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<UserProfileDto> getById(@RequestAttribute("tokenUserId") UUID userId) {
        return ResponseEntity.ok(userService.getById(userId));
    }

    @PatchMapping
    public ResponseEntity<UserProfileDto> update(@RequestAttribute("tokenUserId") UUID userId,
                                                 @Valid @RequestBody UserUpdateDto dto
    ) {
        return ResponseEntity.ok(userService.update(userId, dto));
    }
}
