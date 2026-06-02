package com.smartlockr.support.infrastructure.persistence.repository;

import com.smartlockr.support.infrastructure.persistence.model.entity.Ticket;
import com.smartlockr.support.domain.enums.TicketStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Override
    @EntityGraph(attributePaths = "user")
    List<Ticket> findAll();

    @EntityGraph(attributePaths = "user")
    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    @EntityGraph(attributePaths = "user")
    List<Ticket> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    Optional<Ticket> findWithUserById(UUID id);
}
