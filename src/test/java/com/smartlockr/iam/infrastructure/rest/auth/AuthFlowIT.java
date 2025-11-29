package com.smartlockr.iam.infrastructure.rest.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlockr.iam.application.properties.SecurityProperties;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.SessionResponse;
import com.smartlockr.iam.infrastructure.security.jwt.JwtAdapter;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT {
    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtAdapter jwtAdapter;

    @Autowired
    SecretKey secretKey;

    @Autowired
    SecurityProperties securityProperties;

    @Test
    void me_returns_session_for_valid_auth_cookie() {
        // arrange
        User user = userRepository.save(new User(
                null, "Example User", "example@gmail.com",
                "https://avatar.url/img.png", false, false,
                null, Role.CONSUMER, null
        ));

        // Generamos el token con la duración por defecto (válido)
        String validJwt = jwtAdapter.generateAccessToken(user);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "auth_token=" + validJwt);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // act
        ResponseEntity<SessionResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/auth/me",
                HttpMethod.GET,
                entity,
                SessionResponse.class
        );

        // assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        SessionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.userResponse().email()).isEqualTo(user.getEmail());
    }

    @Test
    void invalid_jwt_signature_should_return_401() {
        // arrange
        String fakeJwt = "eyJhbGciOiJIUzUxMiJ9.e30.Fakesignature_ShouldFail";

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "auth_token=" + fakeJwt);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // act
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/auth/me",
                HttpMethod.GET,
                entity,
                String.class
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expired_jwt_behaviour() throws Exception {
        // arrange
        User user = userRepository.save(new User(
                null, "Expired User", "expired@gmail.com",
                null, false, false,
                null, Role.CONSUMER, null
        ));

        // FABRICAMOS UN TOKEN HISTÓRICO MANUALMENTE
        // 1. Emitido hace 2 horas (IssuedAt)
        // 2. Expirado hace 30 minutos (Expiration)
        // 3. Firmado con la SECRET KEY REAL de la app
        Date issuedAt = Date.from(Instant.now().minus(2, ChronoUnit.HOURS));
        Date expiresAt = Date.from(Instant.now().minus(30, ChronoUnit.MINUTES));

        String expiredJwt = Jwts.builder()
                .audience().add(securityProperties.audience()).and()
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "auth_token=" + expiredJwt);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // act
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/auth/me",
                HttpMethod.GET,
                entity,
                String.class
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String json = response.getBody();
        var node = objectMapper.readTree(json);
        assertAll(
                () -> assertThat(node.get("status").asInt()).isEqualTo(401),
                () -> assertThat(node.get("message").asText()).containsIgnoringCase("expired")
        );
    }
}
