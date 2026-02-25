package com.smartlockr.iam.infrastructure.security.configuration;

import com.smartlockr.shared.properties.SecurityProperties;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
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

/**
 * JWT configuration for Spring Security Resource Server.
 * This configuration focuses on validating incoming JWTs.
 * Responsibilities:
 * - Builds the HMAC {@link SecretKey} from a Base64URL-encoded secret.
 * - Creates a {@link JwtDecoder} configured to use {@code HS512}.
 * - Applies additional token validation (issuer and audience).
 * - Maps JWT claims to Spring Security authorities.
 */
@Configuration
public class JwtConfig {

    /**
     * Creates the HMAC secret key used to verify JWT signatures.
     * The secret is read from {@link SecurityProperties#secretB64()} and decoded using
     * {@link Base64#getUrlDecoder()} (Base64URL). For {@code HS512}, the key must be at least
     * 512 bits (64 bytes); otherwise a {@link WeakKeyException} is thrown.
     * @param securityProperties application security properties
     * @return the decoded {@link SecretKey} to be used by the JWT decoder
     */
    @Bean
    public SecretKey jwtSecretKey(SecurityProperties securityProperties) {
        String b64Key = securityProperties.secretB64();
        if (b64Key == null || b64Key.isEmpty()) {
            throw new IllegalArgumentException("La clave JWT_SECRET no está configurada.");
        }
        byte[] keyBytes = Base64.getUrlDecoder().decode(b64Key);
        if (keyBytes.length < 64) {
            throw new WeakKeyException(
                    "La clave proporcionada tiene " + (keyBytes.length * 8) +
                            " bits. Para HS512 se requieren al menos 512 bits (64 bytes)."
            );
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Creates a composed JWT validator to enforce issuer and audience checks.
     * Validations:
     * - Issuer: validates {@code iss} against {@link SecurityProperties#issuer()}.
     * - Audience: validates that {@code aud} contains {@link SecurityProperties#audience()}.
     * @param securityProperties application security properties
     * @return an {@link OAuth2TokenValidator} to be applied to each decoded {@link Jwt}
     */
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

    /**
     * Builds the {@link JwtDecoder} used by Spring Security to decode and validate JWTs.
     * The decoder is configured to use {@link MacAlgorithm#HS512} and to apply the provided
     * {@link OAuth2TokenValidator} (issuer/audience checks, etc.).
     * @param jwtSecretKey the HMAC secret key
     * @param jwtTokenValidator composed JWT validator
     * @return a configured {@link JwtDecoder}
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, OAuth2TokenValidator<Jwt> jwtTokenValidator) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
        jwtDecoder.setJwtValidator(jwtTokenValidator);
        return jwtDecoder;
    }

    /**
     * Configures how JWT claims are converted into Spring Security authorities.
     * Expected conventions:
     * - The claim {@code role} stores the user's role(s).
     * - {@code ROLE_} prefix is added to align with {@code hasRole(...)} checks.
     * @return a {@link JwtAuthenticationConverter} configured with the authorities converter
     */
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
