package com.minispring.userservice.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor,
                                                               CallOptions callOptions,
                                                               Channel channel) {
       return new ForwardingClientCall.SimpleForwardingClientCall<>(
               channel.newCall(methodDescriptor, callOptions)) {
           @Override
           public void start(Listener<RespT> responseListener, Metadata headers) {
               Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

               if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {

                   headers.put(AUTHORIZATION_KEY, "Bearer " + jwt.getTokenValue());
               }
               super.start(responseListener, headers);
           }
       };
    }
}
