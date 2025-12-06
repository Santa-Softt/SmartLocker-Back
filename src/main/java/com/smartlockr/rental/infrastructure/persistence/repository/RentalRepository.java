package com.smartlockr.rental.infrastructure.persistence.repository;

import com.smartlockr.rental.domain.enums.RentalState;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RentalRepository extends JpaRepository<Rental, UUID> {
    /**
     * Busca un alquiler activo (ACTIVE o HOLD) para un usuario específico.
     * Utilizado por el endpoint /me para determinar si el usuario tiene una sesión de alquiler activa.
     */
    Optional<Rental> findByUserIdAndStateIn(UUID userId, List<RentalState> states);

    /**
     * Busca todos los alquileres en estado HOLD que fueron creados antes de una fecha/hora específica.
     * Esencial para la tarea programada que limpia los HOLDs expirados.
     */
    List<Rental> findAllByStateAndStartTimeBefore(RentalState state, Instant expirationTime);

    /**
     * Busca todos los alquileres en estado ACTIVE cuya hora de finalización estimada ya pasó.
     * Esencial para la tarea programada que aplica penalizaciones.
     */
    List<Rental> findAllByStateAndEstimatedEndTimeBefore(RentalState state, Instant currentTime);
}
