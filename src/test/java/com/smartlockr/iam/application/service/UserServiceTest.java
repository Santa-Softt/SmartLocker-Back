package com.smartlockr.iam.application.service;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import com.smartlockr.shared.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserServiceTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldUpdateUserSettingsAndIncrementVersion() {
        // ARRANGE
        var user = User.builder()
                .fullName("Original Name")
                .email("test@smartlockr.com")
                .hasSeenWelcome(true)
                .role(Role.ADMIN)
                .build();

        var existingUser = userRepository.saveAndFlush(user);
        var userId = existingUser.getId();

        UpdateUserSettings input = new UpdateUserSettings(
                "Updated Name",
                null,
                false,
                true,
                true);

        // ACT
        UserResponse response = userService.updateUserSettings(input, userId);

        // ASSERT
        assertThat(response).isNotNull();
        assertThat(response.fullName()).isEqualTo("Updated Name");
        assertThat(response.hasSeenWelcome()).isFalse();

        User databaseUser = userRepository.findById(userId).orElseThrow();
        assertThat(databaseUser.getFullName()).isEqualTo("Updated Name");
        assertThat(databaseUser.getEmail()).isEqualTo("test@smartlockr.com");
        assertThat(databaseUser.isHasSeenWelcome()).isFalse();
        assertThat(databaseUser.getVersion()).isEqualTo(1L);
    }
}