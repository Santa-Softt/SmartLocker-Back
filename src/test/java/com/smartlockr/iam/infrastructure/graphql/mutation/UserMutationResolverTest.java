package com.smartlockr.iam.infrastructure.graphql.mutation;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.application.service.UserService;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserMutationResolverTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserMutationResolver resolver;

    @Test
    @DisplayName("updateUserSettings - delega en UserService con input y jwt")
    void shouldDelegateUpdateUserSettings() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        Jwt jwt = jwt(userId);
        var input = new UpdateUserSettings("New Name", "https://avatar.io", true, true, false);
        var expected = new UserResponse(userId, "New Name", "user@test.local", "https://avatar.io",
                Role.CONSUMER, true, false, null, true, false);
        given(userService.updateUserSettings(input, jwt)).willReturn(expected);

        var result = resolver.updateUserSettings(input, jwt);

        assertThat(result).isEqualTo(expected);
        then(userService).should().updateUserSettings(input, jwt);
    }

    private Jwt jwt(UUID userId) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", userId.toString())
        );
    }
}
