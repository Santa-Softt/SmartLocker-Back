package com.smartlockr.iam.application.mapper;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import org.mapstruct.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@Mapper(
        componentModel = "spring",
        imports = {
                com.smartlockr.iam.infrastructure.persistence.model.UserPreferences.class,
                com.smartlockr.iam.domain.enums.Role.class
        },
        // los valores que llegan como nulos los ignoramos, referencia 'updateExistingUser'
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    @Mapping(source= "userSettings.receiveReceipts", target = "userPreferences.receiveReceipts")
    @Mapping(source= "userSettings.receivesPromotions", target = "userPreferences.receivesPromotions")
    void updateExistingUser(UpdateUserSettings userSettings, @MappingTarget User user);

    @Mapping(source = "userPreferences.receiveReceipts", target = "receiveReceipts")
    @Mapping(source = "userPreferences.receivesPromotions", target = "receivesPromotions")
    UserResponse toUserResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", source = "oidcUser", qualifiedByName = "payloadToEmail")
    @Mapping(target = "fullName", source = "oidcUser", qualifiedByName = "payloadToFullName")
    @Mapping(target = "avatarUrl", source = "oidcUser", qualifiedByName = "payloadToAvatarUrl")
    @Mapping(target = "role", expression = "java(Role.CONSUMER)")
    @Mapping(target = "hasSeenWelcome", constant = "false")
    @Mapping(target = "suspended", constant = "false")
    @Mapping(target = "suspensionTime", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "rentals", ignore = true)
    @Mapping(target = "userPreferences", expression = "java(new UserPreferences(true, true))")
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
