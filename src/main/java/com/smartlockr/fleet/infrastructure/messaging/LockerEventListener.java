package com.smartlockr.fleet.infrastructure.messaging;

import com.smartlockr.fleet.application.event.LockerStateChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class LockerEventListener {

    // Usamos onBackpressureBuffer para manejar picos de eventos sin perder datos.
    // multicast permite múltiples suscriptores (clientes GraphQL).
    private final Sinks.Many<LockerStateChangedEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Escucha el evento de aplicación y lo emite al flujo reactivo.
     * Este método se ejecuta en el hilo de la transacción que publicó el evento.
     */
    @EventListener
    public void handleLockerStateChange(LockerStateChangedEvent event) {
        // tryEmitNext es thread-safe y comunica el evento al Sink
        sink.tryEmitNext(event);
    }

    /**
     * Expone el flujo como un Flux de solo lectura.
     */
    public Flux<LockerStateChangedEvent> getEventStream() {
        return sink.asFlux();
    }
}
