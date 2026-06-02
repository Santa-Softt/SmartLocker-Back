package com.smartlockr.fleet.infrastructure.persistence.repository;

import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessConfigRepository extends JpaRepository<BusinessConfig, UUID> {
    /**
     * Obtiene la única instancia de configuración de negocio.
     * Como solo habrá una fila en esta tabla, ésto proporciona una forma
     * estandarizada de acceder a ella.
     */
    @Query("SELECT bc FROM BusinessConfig bc")
    Optional<BusinessConfig> findTheOne();
}
