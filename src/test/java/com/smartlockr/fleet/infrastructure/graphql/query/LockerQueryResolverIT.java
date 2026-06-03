package com.smartlockr.fleet.infrastructure.graphql.query;

import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import com.smartlockr.shared.BaseIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.secret-b64=YzNmZGM3ODhiMzM0N2EyMzRkM2Q0ZDNkM2QzZDNkM2QzZDNkM2QzZDNkM2QzZDNkM2QzZDNkM2QzZDNkM2QzZA==",
                "security.issuer=smartlockr-test",
                "security.audience=smartlockr-client",
                "security.access-ttl-duration=1h",
                "security.refresh-ttl-duration=24h",
                "security.refresh-token-byte-size=32",
                "security.oauth-redirect-uri=http://localhost:3000/callback",
                "security.admin-email=admin@smartlockr.com"
        }
)
@AutoConfigureHttpGraphQlTester
class LockerQueryResolverIT extends BaseIntegrationTest {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @MockitoBean
    private FleetService fleetService;

    @MockitoBean
    private BearerTokenResolver bearerTokenResolver;

    @Value("${security.secret-b64}")
    private String secretKeyB64;

    @Value("${security.issuer}")
    private String issuer;

    @Value("${security.audience}")
    private String audience;

    @BeforeEach
    void setup() {

        String validAuthToken = generateValidJwt();

        given(bearerTokenResolver.resolve(any(HttpServletRequest.class)))
                .willReturn(validAuthToken);
    }

    private String generateValidJwt() {
        byte[] keyBytes = Base64.getUrlDecoder().decode(secretKeyB64);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .subject("test-user")
                .issuer(issuer)
                .claim("aud", audience)
                .claim("role", List.of("USER"))
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    @Test
    @DisplayName("Should return available lockers (Integration with Mocked Resolver & HS512)")
    void shouldReturnAvailableLockersBySize() {
        LockerResponse mockResponse = new LockerResponse(
                com.smartlockr.shared.utils.UuidV7.generate(),
                "L-INT-001",
                LockerSize.M,
                LockerState.AVAILABLE,
                new BigDecimal("5.00")
        );

        given(fleetService.findAvailableLockersBySize(LockerSize.M))
                .willReturn(List.of(mockResponse));

        String query = """
            query {
                getAvailableLockersBySize(size: M) {
                    id
                    label
                    size
                    state
                }
            }
        """;

        graphQlTester.document(query)
                .execute()
                .path("getAvailableLockersBySize")
                .entityList(LockerResponse.class)
                .hasSize(1)
                .satisfies(lockers -> assertThat(lockers.getFirst().label()).isEqualTo("L-INT-001"));

        then(fleetService).should().findAvailableLockersBySize(LockerSize.M);
    }

    @Test
    @DisplayName("Should return summaries (Integration with Mocked Resolver & HS512)")
    void shouldReturnLockerSizeSummaries() {
        LockerSizeSummaryResponse summary = new LockerSizeSummaryResponse(
                LockerSize.XL,
                new BigDecimal("15.50"),
                3
        );

        given(fleetService.getLockerSizeSummaries())
                .willReturn(List.of(summary));

        String query = """
            query {
                getLockerSizeSummaries {
                    size
                    hourlyRate
                    availableCount
                }
            }
        """;

        graphQlTester.document(query)
                .execute()
                .path("getLockerSizeSummaries")
                .entityList(LockerSizeSummaryResponse.class)
                .hasSize(1)
                .satisfies(list -> assertThat(list.getFirst().availableCount()).isEqualTo(3));
    }
}