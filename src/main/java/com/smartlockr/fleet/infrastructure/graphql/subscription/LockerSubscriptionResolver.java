package com.smartlockr.fleet.infrastructure.graphql.subscription;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.infrastructure.messaging.LockerEventListener;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;

@GraphQLController
@RequiredArgsConstructor
public class LockerSubscriptionResolver {

    private final LockerEventListener lockerEventListener;
    private final LockerMapper lockerMapper;

    /**
     * Establece un canal de suscripción para recibir actualizaciones ligeras
     * de estado de los lockers en tiempo real.
     *
     * @return Un flujo reactivo (Publisher) que emite LockerUpdateResponse.
     */
    @SubscriptionMapping
    public Publisher<LockerUpdateResponse> onLockerStateChange() {
        return lockerEventListener.getEventStream()
                .map(lockerMapper::toUpdateResponse);
    }
}
