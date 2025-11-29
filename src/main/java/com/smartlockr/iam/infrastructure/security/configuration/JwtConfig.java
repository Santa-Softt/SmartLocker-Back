package com.smartlockr.iam.infrastructure.security.configuration;

import com.smartlockr.iam.application.properties.SecurityProperties;
import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.SecretKey;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(SecurityProperties securityProperties) {
        byte[] keyBytes = Base64.getUrlDecoder().decode(securityProperties.secretB64());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator(SecurityProperties securityProperties) {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(securityProperties.issuer());
        OAuth2TokenValidator<Jwt> withAudience = token -> {
            if (token.getAudience().contains((securityProperties.audience()))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "The required audience is missing",
                    null
            ));
        };
        return new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, OAuth2TokenValidator<Jwt> jwtTokenValidator) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
        jwtDecoder.setJwtValidator(jwtTokenValidator);
        return jwtDecoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return jwtConverter;
    }
}
