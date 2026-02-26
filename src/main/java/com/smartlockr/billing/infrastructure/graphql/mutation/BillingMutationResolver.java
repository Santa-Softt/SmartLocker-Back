package com.smartlockr.billing.infrastructure.graphql.mutation;

import com.smartlockr.billing.application.service.BillingService;
import com.smartlockr.billing.infrastructure.dto.PaymentLinkResponse;
import com.smartlockr.commons.annotations.GraphQLController;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;

import java.util.UUID;

@GraphQLController
@RequiredArgsConstructor
public class BillingMutationResolver {
    private final BillingService billingService;

    @MutationMapping
    public PaymentLinkResponse createPaymentOrder(@Argument UUID rentalId){
        return billingService.createPaymentOrder(rentalId);
    }
}
