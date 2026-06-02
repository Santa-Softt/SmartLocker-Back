package com.smartlockr.support.application.service;

import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.model.UserPreferences;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.shared.PostgresContainerIntegrationTest;
import com.smartlockr.support.domain.enums.TicketPriority;
import com.smartlockr.support.domain.enums.TicketStatus;
import com.smartlockr.support.infrastructure.graphql.dto.ReportProblemInput;
import com.smartlockr.support.infrastructure.persistence.repository.SystemLogRepository;
import com.smartlockr.support.infrastructure.persistence.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupportServicePostgresTest extends PostgresContainerIntegrationTest {

    @Autowired
    private SupportService supportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Test
    void reportProblemPersistsTicketAndAuditLogInPostgres() {
        User user = userRepository.save(User.builder()
                .email("support-it@smartlockr.com")
                .fullName("Support IT")
                .avatarUrl("avatar.png")
                .role(Role.CONSUMER)
                .hasSeenWelcome(true)
                .suspended(false)
                .userPreferences(new UserPreferences(true, false))
                .build());

        UUID rentalId = UUID.randomUUID();
        var input = new ReportProblemInput("Locker blocked", "The locker door is stuck.", rentalId, TicketPriority.HIGH);

        var response = supportService.reportProblem(input, user.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticketRepository.findById(response.id())).isPresent();
        assertThat(systemLogRepository.findAll()).hasSize(1);
    }
}
