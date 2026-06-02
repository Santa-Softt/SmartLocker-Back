package com.smartlockr.support.application.service;

import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.support.domain.enums.TicketPriority;
import com.smartlockr.support.domain.enums.TicketStatus;
import com.smartlockr.support.infrastructure.graphql.dto.ReportProblemInput;
import com.smartlockr.support.infrastructure.graphql.dto.TicketResponse;
import com.smartlockr.support.infrastructure.graphql.dto.UpdateTicketStatusInput;
import com.smartlockr.support.infrastructure.persistence.model.entity.Ticket;
import com.smartlockr.support.infrastructure.persistence.repository.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class SupportService {

    private static final String ACTION_REPORT_PROBLEM = "REPORT_PROBLEM";
    private static final String ACTION_UPDATE_TICKET_STATUS = "UPDATE_TICKET_STATUS";
    private static final String RESOURCE_TICKET = "SUPPORT_TICKET";

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public TicketResponse reportProblem(@Valid ReportProblemInput input, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Ticket ticket = Ticket.builder()
                .user(user)
                .subject(input.subject())
                .description(input.description())
                .rentalId(input.rentalId())
                .priority(input.priority() == null ? TicketPriority.MEDIUM : input.priority())
                .status(TicketStatus.OPEN)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);

        auditService.record(
                user,
                ACTION_REPORT_PROBLEM,
                RESOURCE_TICKET,
                savedTicket.getId().toString(),
                "Support ticket created"
        );

        return toResponse(savedTicket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> findTicketsForAdmin(TicketStatus status) {
        List<Ticket> tickets = status == null
                ? ticketRepository.findAllByOrderByCreatedAtDesc()
                : ticketRepository.findByStatusOrderByCreatedAtDesc(status);

        return tickets.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse findTicketForAdmin(UUID ticketId) {
        return ticketRepository.findWithUserById(ticketId)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
    }

    @Transactional
    public TicketResponse updateTicketStatus(@Valid UpdateTicketStatusInput input, UUID adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found"));

        Ticket ticket = ticketRepository.findWithUserById(input.ticketId())
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + input.ticketId()));

        TicketStatus previousStatus = ticket.getStatus();
        ticket.setStatus(input.status());
        Ticket savedTicket = ticketRepository.save(ticket);

        auditService.record(
                admin,
                ACTION_UPDATE_TICKET_STATUS,
                RESOURCE_TICKET,
                savedTicket.getId().toString(),
                "Ticket status changed from %s to %s".formatted(previousStatus, input.status())
        );

        return toResponse(savedTicket);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getUser().getId(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getRentalId(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
