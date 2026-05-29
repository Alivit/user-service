package com.minispring.userservice.service.impl;

import com.minispring.grpc.AuthGrpcServiceGrpc;
import com.minispring.grpc.UserStatusRequest;
import com.minispring.grpc.UserStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthGrpcService {

    private final AuthGrpcServiceGrpc.AuthGrpcServiceBlockingStub authGrpc;

    public void setStatus(UUID userId, boolean enabled) {
        log.debug("Sending status sync request for user: {}, enabled: {}", userId, enabled);

        UserStatusRequest request = UserStatusRequest.newBuilder()
                .setUserId(userId.toString())
                .setEnabled(enabled)
                .build();
        try {
            UserStatusResponse response = authGrpc.changeStatus(request);
            if (response.getSuccess()) {
                log.debug("Status sync successful for user: {}", userId);
            }
        } catch (Exception e) {
            throw new RuntimeException("gRPC auth-service sync failed", e);
        }
    }
}
