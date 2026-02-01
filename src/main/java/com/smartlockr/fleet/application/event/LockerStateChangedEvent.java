package com.smartlockr.fleet.application.event;

import com.smartlockr.fleet.domain.enums.LockerState;

import java.util.UUID;

/**
 * Evento inmutable y ligero. Solo transporta lo que cambió.
 */
public record LockerStateChangedEvent(
        UUID lockerId,
        LockerState newState
) {}