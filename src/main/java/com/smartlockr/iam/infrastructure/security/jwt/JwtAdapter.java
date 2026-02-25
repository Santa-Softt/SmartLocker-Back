package com.smartlockr.iam.infrastructure.security.jwt;

import com.smartlockr.iam.application.auth.port.JwtProvider;
import com.smartlockr.iam.infrastructure.persistence.model.SecurityUser;
import com.smartlockr.shared.properties.SecurityProperties;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

 
/**
 * JWT provider adapter that issues access tokens using a symmetric HMAC secret.
 * Tokens are signed with HS512 and include issuer, audience, subject, expiration and roles.
 */
@Component
@RequiredArgsConstructor
public class JwtAdapter implements JwtProvider {

    private final SecretKey jwtSecretKey;
    private final SecurityProperties securityProperties;

 
    /**
     * Generates a signed access token for the given user.
     * The token includes:
     * - subject: user id
     * - issuer: configured issuer
     * - aud: configured audience
     * - role: list of Spring Security authorities
     * - iat/exp: issued-at and expiration timestamps
     * @param user application user
     * @return compact JWT string
     */
    @Override
    public String generateAccessToken(User user) {
        var userSubject = String.valueOf(user.getId());
        var securityUser = new SecurityUser(user);
        List<String> roles = securityUser.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Date now = Date.from(Instant.now());
        Date expiresAt = Date.from(Instant.now()
                .plus(securityProperties.accessTtlDuration()));
        var issuer = securityProperties.issuer();
        var audience = securityProperties.audience();

        return Jwts.builder()
                .subject(userSubject)
                .issuer(issuer)
                .claim("aud", audience)
                .issuedAt(now)
                .expiration(expiresAt)
                .claim("role", roles)
                .signWith(jwtSecretKey, Jwts.SIG.HS512)
                .compact();
    }
}
