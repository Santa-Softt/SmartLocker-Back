package com.smartlockr.fleet.infrastructure.graphql.query;

import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@GraphQlTest(LockerQueryResolver.class)
class LockerQueryResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private FleetService fleetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /**
     * Prueba el escenario donde se solicitan lockers disponibles por tamaño
     */
    @Test
    @DisplayName("Should return available lockers for specific size (BDD Style)")
    void shouldReturnAvailableLockersBySize() {
        LockerResponse mockResponse = new LockerResponse(
                com.smartlockr.shared.utils.UuidV7.generate(),
                "L-101",
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
                .satisfies(lockers -> {
                    LockerResponse locker = lockers.getFirst();
                    assertThat(locker.label()).isEqualTo("L-101");
                    assertThat(locker.size()).isEqualTo(LockerSize.M);
                });

        then(fleetService).should().findAvailableLockersBySize(LockerSize.M);
    }

    /**
     * Prueba la obtención del resumen de tamaños utilizando sintaxis BDD.
     */
    @Test
    @DisplayName("Should return locker size summaries (BDD Style)")
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
                .satisfies(summaries -> {
                    LockerSizeSummaryResponse s = summaries.getFirst();
                    assertThat(s.size()).isEqualTo(LockerSize.XL);
                    assertThat(s.availableCount()).isEqualTo(3);
                });

        then(fleetService).should().getLockerSizeSummaries();
    }

    /**
     * Verifica el comportamiento cuando el servicio retorna una lista vacía.
     */
    @Test
    @DisplayName("Should return empty list when service returns no results")
    void shouldReturnEmptyList() {
        given(fleetService.findAvailableLockersBySize(any()))
                .willReturn(List.of());

        String query = """
            query {
                getAvailableLockersBySize(size: S) {
                    id
                }
            }
        """;

        graphQlTester.document(query)
                .execute()
                .path("getAvailableLockersBySize")
                .entityList(LockerResponse.class)
                .hasSize(0);

        then(fleetService).should().findAvailableLockersBySize(LockerSize.S);
    }
}