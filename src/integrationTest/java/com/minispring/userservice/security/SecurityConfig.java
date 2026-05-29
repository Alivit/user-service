package com.minispring.userservice.security;

import com.minispring.userservice.model.User;
import org.instancio.Instancio;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.instancio.Select.field;
import static org.mockito.Mockito.when;

public class SecurityConfig {

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    protected Jwt createToken(String name, String role){
        Jwt jwt = Jwt.withTokenValue(name)
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("preferred_username", name)
                .claim("realm_access", Map.of("roles", List.of(role.toUpperCase())))
                .build();
        when(jwtDecoder.decode(name)).thenReturn(jwt);

        return jwt;
    }

    protected User createSecureUser(String authId){
        return Instancio.of(User.class)
                .set(field(User::getId), UUID.fromString(authId))
                .generate(field(User::getEmail), gen -> gen.text().pattern("#c#c#c#c#c#c#c#c@domain.com"))
                .set(field(User::getCards), new ArrayList<>())
                .ignore(field(User::getVersion))
                .create();
    }
}
