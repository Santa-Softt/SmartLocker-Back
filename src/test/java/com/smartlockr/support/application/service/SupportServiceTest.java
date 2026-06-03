package com.smartlockr.support.application.service;

import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.model.UserPreferences;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.support.domain.enums.TicketPriority;
import com.smartlockr.support.domain.enums.TicketStatus;
import com.smartlockr.support.infrastructure.graphql.dto.ReportProblemInput;
import com.smartlockr.support.infrastructure.graphql.dto.UpdateTicketStatusInput;
import com.smartlockr.support.infrastructure.persistence.model.entity.Ticket;
import com.smartlockr.support.infrastructure.persistence.repository.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private SupportService supportService;

    @BeforeEach
    void setUp() {
        supportService = new SupportService(ticketRepository, userRepository, auditService);
    }

    @Test
    @DisplayName("reportProblem - happy path persiste ticket y registra audit log")
    void shouldPersistTicketAndAuditLog() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        User user = user(userId);
        var input = new ReportProblemInput("Locker broken", "Door stuck", com.smartlockr.shared.utils.UuidV7.generate(), TicketPriority.HIGH);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(ticketRepository.save(any(Ticket.class))).willAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(com.smartlockr.shared.utils.UuidV7.generate());
            t.setCreatedAt(java.time.Instant.now());
            t.setUpdatedAt(java.time.Instant.now());
            return t;
        });

        var response = supportService.reportProblem(input, userId);

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(response.subject()).isEqualTo("Locker broken");
        assertThat(response.description()).isEqualTo("Door stuck");
        verify(auditService).record(eq(user), eq("REPORT_PROBLEM"), eq("SUPPORT_TICKET"),
                any(String.class), eq("Support ticket created"));
    }

    @Test
    @DisplayName("reportProblem - prioridad null usa MEDIUM por defecto")
    void shouldDefaultToMediumPriority() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        User user = user(userId);
        var input = new ReportProblemInput("Subject", "Description", com.smartlockr.shared.utils.UuidV7.generate(), null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(ticketRepository.save(any(Ticket.class))).willAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(com.smartlockr.shared.utils.UuidV7.generate());
            t.setCreatedAt(java.time.Instant.now());
            t.setUpdatedAt(java.time.Instant.now());
            return t;
        });

        var response = supportService.reportProblem(input, userId);

        assertThat(response.priority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    @DisplayName("reportProblem - usuario inexistente lanza UsernameNotFoundException")
    void shouldThrowWhenUserNotFound() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        var input = new ReportProblemInput("Subject", "Description", com.smartlockr.shared.utils.UuidV7.generate(), TicketPriority.LOW);
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.reportProblem(input, userId))
                .isInstanceOf(UsernameNotFoundException.class);

        then(ticketRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("findTicketsForAdmin - sin filtro devuelve todos los tickets")
    void shouldReturnAllTicketsWhenStatusIsNull() {
        Ticket t1 = ticket(com.smartlockr.shared.utils.UuidV7.generate(), TicketStatus.OPEN);
        Ticket t2 = ticket(com.smartlockr.shared.utils.UuidV7.generate(), TicketStatus.CLOSED);
        given(ticketRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(t1, t2));

        var result = supportService.findTicketsForAdmin(null);

        assertThat(result).hasSize(2);
        then(ticketRepository).should().findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("findTicketsForAdmin - con status filtra por estado")
    void shouldFilterTicketsByStatus() {
        Ticket t1 = ticket(com.smartlockr.shared.utils.UuidV7.generate(), TicketStatus.OPEN);
        given(ticketRepository.findByStatusOrderByCreatedAtDesc(TicketStatus.OPEN)).willReturn(List.of(t1));

        var result = supportService.findTicketsForAdmin(TicketStatus.OPEN);

        assertThat(result).hasSize(1);
        then(ticketRepository).should().findByStatusOrderByCreatedAtDesc(TicketStatus.OPEN);
    }

    @Test
    @DisplayName("findTicketForAdmin - existente devuelve respuesta")
    void shouldReturnTicketWhenFound() {
        UUID ticketId = com.smartlockr.shared.utils.UuidV7.generate();
        Ticket t = ticket(ticketId, TicketStatus.OPEN);
        given(ticketRepository.findWithUserById(ticketId)).willReturn(Optional.of(t));

        var result = supportService.findTicketForAdmin(ticketId);

        assertThat(result.id()).isEqualTo(ticketId);
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    @DisplayName("findTicketForAdmin - no encontrado lanza EntityNotFoundException")
    void shouldThrowWhenTicketNotFound() {
        UUID ticketId = com.smartlockr.shared.utils.UuidV7.generate();
        given(ticketRepository.findWithUserById(ticketId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.findTicketForAdmin(ticketId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(ticketId.toString());
    }

    @Test
    @DisplayName("updateTicketStatus - happy path cambia estado y registra audit log")
    void shouldUpdateTicketStatus() {
        UUID ticketId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID adminId = com.smartlockr.shared.utils.UuidV7.generate();
        User admin = user(adminId);
        admin.setRole(Role.ADMIN);
        Ticket ticket = ticket(ticketId, TicketStatus.OPEN);
        var input = new UpdateTicketStatusInput(ticketId, TicketStatus.CLOSED);

        given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
        given(ticketRepository.findWithUserById(ticketId)).willReturn(Optional.of(ticket));
        given(ticketRepository.save(ticket)).willReturn(ticket);

        var result = supportService.updateTicketStatus(input, adminId);

        assertThat(result.status()).isEqualTo(TicketStatus.CLOSED);
        verify(auditService).record(eq(admin), eq("UPDATE_TICKET_STATUS"), eq("SUPPORT_TICKET"),
                eq(ticketId.toString()),
                org.mockito.ArgumentMatchers.contains("OPEN to CLOSED"));
    }

    @Test
    @DisplayName("updateTicketStatus - admin no encontrado lanza UsernameNotFoundException")
    void shouldThrowWhenAdminNotFound() {
        UUID adminId = com.smartlockr.shared.utils.UuidV7.generate();
        var input = new UpdateTicketStatusInput(com.smartlockr.shared.utils.UuidV7.generate(), TicketStatus.CLOSED);
        given(userRepository.findById(adminId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.updateTicketStatus(input, adminId))
                .isInstanceOf(UsernameNotFoundException.class);

        then(ticketRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("updateTicketStatus - ticket no encontrado lanza EntityNotFoundException")
    void shouldThrowWhenTicketNotFoundOnUpdate() {
        UUID adminId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID ticketId = com.smartlockr.shared.utils.UuidV7.generate();
        User admin = user(adminId);
        var input = new UpdateTicketStatusInput(ticketId, TicketStatus.CLOSED);

        given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
        given(ticketRepository.findWithUserById(ticketId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.updateTicketStatus(input, adminId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private User user(UUID id) {
        return User.builder()
                .id(id)
                .email("u@test.local")
                .fullName("User")
                .role(Role.CONSUMER)
                .userPreferences(new UserPreferences(true, false))
                .build();
    }

    private Ticket ticket(UUID id, TicketStatus status) {
        return Ticket.builder()
                .id(id)
                .subject("Subj")
                .description("Desc")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .user(user(com.smartlockr.shared.utils.UuidV7.generate()))
                .build();
    }
}
