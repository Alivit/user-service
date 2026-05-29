package com.minispring.userservice.client;

import com.minispring.grpc.service.AuthGrpcServiceGrpc;
import com.minispring.grpc.service.DeleteUserRequest;
import com.minispring.grpc.service.DeleteUserResponse;
import com.minispring.grpc.service.UserStatusRequest;
import com.minispring.grpc.service.UserStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthGrpcClient {

    private final AuthGrpcServiceGrpc.AuthGrpcServiceBlockingStub authGrpc;

    public void setStatus(UUID userId, boolean enabled) {
        log.debug("Sending status sync request for user: {}, enabled: {}", userId, enabled);

        UserStatusRequest request = UserStatusRequest.newBuilder()
                .setUserId(userId.toString())
                .setEnabled(enabled)
                .build();
        try {
            UserStatusResponse response = authGrpc.withDeadlineAfter(12, TimeUnit.SECONDS).changeStatus(request);
            if (response.getSuccess()) {
                log.debug("Status sync successful for user: {}", userId);
            }
        } catch (Exception e) {
            throw new RuntimeException("gRPC auth-service sync failed", e);
        }
    }

    public void deleteUser(UUID userId) {
        log.debug("Sending hard-delete request to Keycloak for user: {}", userId);

        DeleteUserRequest request = DeleteUserRequest.newBuilder()
                .setUserId(userId.toString())
                .build();
        try {
            DeleteUserResponse response = authGrpc.withDeadlineAfter(12, TimeUnit.SECONDS).deleteUser(request);
            if (!response.getSuccess()) {
                throw new RuntimeException("Keycloak returned success=false during user deletion");
            }
        } catch (Exception e) {
            throw new RuntimeException("gRPC Keycloak delete operation failed", e);
        }
    }
}
