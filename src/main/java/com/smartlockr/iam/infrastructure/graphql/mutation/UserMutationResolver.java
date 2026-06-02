package com.smartlockr.iam.infrastructure.graphql.mutation;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.application.service.UserService;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@GraphQLController
@RequiredArgsConstructor
public class UserMutationResolver {

    private final UserService userService;

    @MutationMapping
    public UserResponse updateUserSettings(@Argument UpdateUserSettings input, @AuthenticationPrincipal Jwt userJwt){
        return userService.updateUserSettings(input, userJwt);
    }
}
