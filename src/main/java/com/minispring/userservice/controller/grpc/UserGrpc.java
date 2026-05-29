package com.minispring.userservice.controller.grpc;

import com.minispring.grpc.service.GetUserByEmailRequest;
import com.minispring.grpc.service.GetUserByEmailResponse;
import com.minispring.grpc.service.GetUserByIdRequest;
import com.minispring.grpc.service.GetUserByIdResponse;
import com.minispring.grpc.service.GetUsersByIdsRequest;
import com.minispring.grpc.service.GetUsersByIdsResponse;
import com.minispring.grpc.service.UserDto;
import com.minispring.grpc.service.UserGrpcServiceGrpc;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.service.UserService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_NOT_FOUND;

@Slf4j
@GrpcService
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class UserGrpc extends UserGrpcServiceGrpc.UserGrpcServiceImplBase {

    private final UserService userService;

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<GetUserByEmailResponse> responseObserver) {
        try {
            UserDto userDto = userService.getByEmailForGrpc(request.getEmail());

            GetUserByEmailResponse response = GetUserByEmailResponse.newBuilder()
                    .setUser(userDto)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (ResourceNotFoundException ex) {
            log.warn(String.format(EMAIL_NOT_FOUND, request.getEmail()));
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            log.error("Unexpected error in getUserByEmail for email={}", request.getEmail(), ex);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").withCause(ex).asRuntimeException());
        }
    }

    @Override
    public void getUserById(GetUserByIdRequest request, StreamObserver<GetUserByIdResponse> responseObserver) {
        try {
            UserDto userDto = userService.getByIdForGrpc(UUID.fromString(request.getUserId()));

            GetUserByIdResponse response = GetUserByIdResponse.newBuilder()
                    .setUser(userDto)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (ResourceNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).withCause(ex).asRuntimeException());
        } catch (Exception ex) {
            log.error("Unexpected error in getUserById for id={}", request.getUserId(), ex);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").withCause(ex).asRuntimeException());
        }
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void getUsersByIds(GetUsersByIdsRequest request, StreamObserver<GetUsersByIdsResponse> responseObserver) {
        try {
            List<UUID> uuids = request.getUserIdsList().stream()
                    .map(UUID::fromString)
                    .toList();

            List<UserDto> usersDto = userService.getUsersByIdsForGrpc(uuids);

            GetUsersByIdsResponse response = GetUsersByIdsResponse.newBuilder()
                    .addAllUsers(usersDto)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception ex) {
            log.error("Unexpected error in getUsersByIds gRPC method", ex);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").withCause(ex).asRuntimeException());
        }
    }
}
