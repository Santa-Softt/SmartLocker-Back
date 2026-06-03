package com.smartlockr.fleet.infrastructure.graphql.subscription;

import com.smartlockr.fleet.application.event.LockerStateChangedEvent;
import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.messaging.LockerEventListener;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerUpdateResponse;
import com.smartlockr.shared.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.socket.client.StandardWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.secret-b64=kCXyHBUc-_zMc53qbkp-Urdl0QrZcNzOiZcnZK-ztBRhKRQPSGqBevVMKXywKgH2dhcXxhKPVS-N8qSlZX5XFw==",
                "security.issuer=smartlockr-test",
                "security.audience=smartlockr-client",
                "security.access-ttl-duration=1h",
                "security.refresh-ttl-duration=24h",
                "security.refresh-token-byte-size=32",
                "security.oauth-redirect-uri=http://localhost:3000/callback",
                "security.admin-email=admin@smartlockr.com"
        }
)
class LockerSubscriptionResolverIT extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private LockerEventListener lockerEventListener;

    @MockitoBean
    private LockerMapper lockerMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private WebSocketGraphQlTester graphQlTester;
    private Sinks.Many<LockerStateChangedEvent> lockerEventSink;

    @BeforeEach
    void setup() {
        lockerEventSink = Sinks.many().multicast().onBackpressureBuffer();

        doReturn(lockerEventSink.asFlux())
                .when(lockerEventListener).getEventStream();

        UUID lockerId = com.smartlockr.shared.utils.UuidV7.generate();
        LockerUpdateResponse mockResponse = new LockerUpdateResponse(lockerId, LockerState.OCCUPIED);

        when(lockerMapper.toUpdateResponse(any(LockerStateChangedEvent.class))).thenReturn(mockResponse);

        Jwt mockJwt = new Jwt(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "test-user", "roles", "USER")
        );

        when(jwtDecoder.decode("mock-token")).thenReturn(mockJwt);

        URI url = URI.create("ws://localhost:" + port + "/graphql");
        this.graphQlTester = WebSocketGraphQlTester.builder(url, new StandardWebSocketClient())
                .headers(headers -> headers.add("Cookie", "auth_token=mock-token"))
                .build();
    }

    /**
     * Valida que la suscripción reciba un evento de dominio, lo transforme y lo entregue al cliente.
     */
    @Test
    @DisplayName("Should receive mapped LockerUpdateResponse when LockerStateChangedEvent is emitted")
    void shouldReceiveMappedLockerUpdates() {
        String subscription = """
            subscription {
                onLockerStateChange {
                    id
                    state
                }
            }
        """;

        Flux<LockerUpdateResponse> resultFlux = graphQlTester.document(subscription)
                .executeSubscription()
                .toFlux("onLockerStateChange", LockerUpdateResponse.class);

        LockerStateChangedEvent mockEvent = new LockerStateChangedEvent(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED);

        StepVerifier.create(resultFlux)
                .then(() -> lockerEventSink.tryEmitNext(mockEvent))
                .expectNextMatches(response ->
                        response.id() != null &&
                                response.state() == LockerState.OCCUPIED)
                .thenCancel()
                .verify();
    }

}