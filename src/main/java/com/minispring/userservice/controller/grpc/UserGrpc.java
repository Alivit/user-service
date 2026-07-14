package com.minispring.userservice.controller.grpc;

import com.minispring.grpc.service.GetUserByEmailRequest;
import com.minispring.grpc.service.GetUserByEmailResponse;
import com.minispring.grpc.service.GetUserByIdRequest;
import com.minispring.grpc.service.GetUserByIdResponse;
import com.minispring.grpc.service.GetUsersByIdsRequest;
import com.minispring.grpc.service.GetUsersByIdsResponse;
import com.minispring.grpc.service.UserDto;
import com.minispring.grpc.service.UserGrpcServiceGrpc;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.mapper.GrpcUserMapper;
import com.minispring.userservice.service.UserService;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.access.prepost.PreAuthorize;

@Slf4j
@GrpcService
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN', 'INTERNAL_SERVICE')")
public class UserGrpc extends UserGrpcServiceGrpc.UserGrpcServiceImplBase {

    private final UserService userService;
    private final GrpcUserMapper grpcUserMapper;

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<GetUserByEmailResponse> responseObserver) {
        UserView profile = userService.getHistoricalUserByEmail(request.getEmail());
        UserDto userDto = grpcUserMapper.toGrpcUserDto(profile);

        GetUserByEmailResponse response =
                GetUserByEmailResponse.newBuilder().setUser(userDto).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getUserById(GetUserByIdRequest request, StreamObserver<GetUserByIdResponse> responseObserver) {
        UserView profile = userService.getHistoricalUserById(UUID.fromString(request.getUserId()));
        UserDto userDto = grpcUserMapper.toGrpcUserDto(profile);

        GetUserByIdResponse response =
                GetUserByIdResponse.newBuilder().setUser(userDto).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERNAL_SERVICE')")
    public void getUsersByIds(GetUsersByIdsRequest request, StreamObserver<GetUsersByIdsResponse> responseObserver) {
        List<UUID> uuids =
                request.getUserIdsList().stream().map(UUID::fromString).toList();

        List<UserView> profiles = userService.getHistoricalUsersByIds(uuids);

        List<UserDto> usersDto =
                profiles.stream().map(grpcUserMapper::toGrpcUserDto).toList();

        GetUsersByIdsResponse response =
                GetUsersByIdsResponse.newBuilder().addAllUsers(usersDto).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
