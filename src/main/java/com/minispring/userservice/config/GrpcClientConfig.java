package com.minispring.userservice.config;

import com.minispring.grpc.AuthGrpcServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public AuthGrpcServiceGrpc.AuthGrpcServiceBlockingStub userStatusGrpcStub(GrpcChannelFactory channelFactory) {
        return AuthGrpcServiceGrpc.newBlockingStub(channelFactory.createChannel("auth-service"));
    }
}
