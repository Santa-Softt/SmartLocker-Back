package com.smartlockr.iam.application.mapper;

import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    UserResponse toUserResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", source = "oidcUser", qualifiedByName = "payloadToEmail")
    @Mapping(target = "fullName", source = "oidcUser", qualifiedByName = "payloadToFullName")
    @Mapping(target = "avatarUrl", source = "oidcUser", qualifiedByName = "payloadToAvatarUrl")
    @Mapping(target = "role", expression = "java(com.smartlockr.iam.domain.enums.Role.CONSUMER)")
    @Mapping(target = "hasSeenWelcome", constant = "false")
    @Mapping(target = "suspended", constant = "false")
    @Mapping(target = "suspensionTime", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    User toNewUser(OidcUser oidcUser);

    @Named("payloadToFullName")
    default String payloadToFullName(OidcUser oidcUser) {
        return oidcUser.getFullName();
    }

    @Named("payloadToAvatarUrl")
    default String payloadToAvatarUrl(OidcUser oidcUser) {
        return oidcUser.getPicture();
    }

    @Named("payloadToEmail")
    default String payloadToEmail(OidcUser oidcUser) {
        return oidcUser.getEmail();
    }
}
