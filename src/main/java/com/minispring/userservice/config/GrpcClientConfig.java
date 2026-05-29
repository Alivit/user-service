package com.minispring.userservice.config;

import com.minispring.grpc.service.AuthGrpcServiceGrpc;
import com.minispring.userservice.client.BearerTokenInterceptor;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public AuthGrpcServiceGrpc.AuthGrpcServiceBlockingStub userStatusGrpcStub(
            GrpcChannelFactory channelFactory,
            BearerTokenInterceptor bearerTokenInterceptor) {
        Channel channel = channelFactory.createChannel("auth-service-grpc");
        Channel interceptedChannel = ClientInterceptors.intercept(channel, bearerTokenInterceptor);
        return AuthGrpcServiceGrpc.newBlockingStub(interceptedChannel);
    }
}
