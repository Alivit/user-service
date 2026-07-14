package com.minispring.userservice.client;

import com.minispring.grpc.service.AuthGrpcServiceGrpc;
import com.minispring.grpc.service.DeleteUserRequest;
import com.minispring.grpc.service.UserStatusRequest;
import com.minispring.userservice.exception.ResourceNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

        executeGrpcCall(() -> authGrpc.withDeadlineAfter(3, TimeUnit.SECONDS).changeStatus(request), userId);
        log.debug("Status sync successful for user: {}", userId);
    }

    public void deleteUser(UUID userId) {
        log.debug("Sending hard-delete request to Keycloak for user: {}", userId);

        DeleteUserRequest request =
                DeleteUserRequest.newBuilder().setUserId(userId.toString()).build();

        executeGrpcCall(() -> authGrpc.withDeadlineAfter(3, TimeUnit.SECONDS).deleteUser(request), userId);
        log.debug("User {} successfully hard-deleted from Keycloak", userId);
    }

    private <T> void executeGrpcCall(Supplier<T> grpcCall, UUID userId) {
        try {
            grpcCall.get();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("User " + userId + " not found in Keycloak");
            }
            log.error(
                    "CRITICAL: gRPC call to auth-service failed. Status: {}, Reason: {}",
                    ex.getStatus().getCode(),
                    ex.getMessage());
            throw ex;
        }
    }
}
