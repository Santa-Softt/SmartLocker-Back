package com.smartlockr.billing.infrastructure.graphql.mutation;

import com.smartlockr.billing.application.service.BillingService;
import com.smartlockr.billing.infrastructure.dto.PaymentLinkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BillingMutationResolverTest {

    @Mock
    private BillingService billingService;
    @Mock
    private Jwt jwt;

    private BillingMutationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BillingMutationResolver(billingService);
    }

    @Test
    @DisplayName("createPaymentOrder - rejects unauthenticated users")
    void shouldRejectUnauthenticatedCreatePaymentOrder() {
        assertThatThrownBy(() -> resolver.createPaymentOrder(UUID.randomUUID(), null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("createPaymentOrder - delegates authenticated users")
    void shouldDelegateCreatePaymentOrder() {
        UUID userId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        PaymentLinkResponse expected = new PaymentLinkResponse("https://pay.test");
        given(jwt.getSubject()).willReturn(userId.toString());
        given(billingService.createPaymentOrder(rentalId, userId)).willReturn(expected);

        assertThat(resolver.createPaymentOrder(rentalId, jwt)).isEqualTo(expected);
    }

    @Test
    @DisplayName("createPenaltyPaymentOrder - delegates authenticated users")
    void shouldDelegatePenaltyPaymentOrder() {
        UUID userId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        PaymentLinkResponse expected = new PaymentLinkResponse("https://pay.test/penalty");
        given(jwt.getSubject()).willReturn(userId.toString());
        given(billingService.createPenaltyPaymentOrder(rentalId, userId)).willReturn(expected);

        assertThat(resolver.createPenaltyPaymentOrder(rentalId, jwt)).isEqualTo(expected);
    }
}
