package com.smartlockr.rental.infrastructure.graphql.mutation;

import com.smartlockr.billing.infrastructure.dto.PaymentLinkResponse;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.rental.application.service.RentalService;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalHoldResponse;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RentalMutationResolverTest {

    @Mock
    private RentalService rentalService;
    @Mock
    private Jwt jwt;

    private RentalMutationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RentalMutationResolver(rentalService);
    }

    @Test
    @DisplayName("initiateHold - rejects unauthenticated users")
    void shouldRejectUnauthenticatedInitiateHold() {
        assertThatThrownBy(() -> resolver.initiateHold(LockerSize.M, 60, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(rentalService);
    }

    @Test
    @DisplayName("initiateHold - delegates authenticated request")
    void shouldDelegateInitiateHold() {
        RentalResponse expected = new RentalResponse(
                UUID.randomUUID(),
                null,
                Instant.now(),
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(300),
                false,
                BigDecimal.TEN
        );
        given(jwt.getSubject()).willReturn("user-id");
        given(rentalService.initiateHold(LockerSize.M, "user-id", 60)).willReturn(expected);

        RentalResponse result = resolver.initiateHold(LockerSize.M, 60, jwt);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("cancelLockerHold - delegates authenticated request")
    void shouldDelegateCancelHold() {
        UUID userId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        RentalHoldResponse expected = new RentalHoldResponse("ok");
        given(jwt.getSubject()).willReturn(userId.toString());
        given(rentalService.cancelUserHold(rentalId, userId)).willReturn(expected);

        assertThat(resolver.cancelLockerHold(rentalId, jwt)).isEqualTo(expected);
    }

    @Test
    @DisplayName("releaseLocker - delegates authenticated request")
    void shouldDelegateReleaseLocker() {
        UUID userId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        RentalHoldResponse expected = new RentalHoldResponse("ok");
        given(jwt.getSubject()).willReturn(userId.toString());
        given(rentalService.releaseLocker(rentalId, userId)).willReturn(expected);

        assertThat(resolver.releaseLocker(rentalId, jwt)).isEqualTo(expected);
    }

    @Test
    @DisplayName("createExtensionPaymentOrder - delegates authenticated request")
    void shouldDelegateCreateExtensionPaymentOrder() {
        UUID userId = UUID.randomUUID();
        UUID rentalId = UUID.randomUUID();
        PaymentLinkResponse expected = new PaymentLinkResponse("https://pay.test");
        given(jwt.getSubject()).willReturn(userId.toString());
        given(rentalService.createExtensionPaymentOrder(rentalId, userId, 30)).willReturn(expected);

        assertThat(resolver.createExtensionPaymentOrder(rentalId, 30, jwt)).isEqualTo(expected);
        then(rentalService).should().createExtensionPaymentOrder(rentalId, userId, 30);
    }
}
