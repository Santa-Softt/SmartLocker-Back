package com.smartlockr.iam.application.mapper;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.model.UserPreferences;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import org.mapstruct.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    @Mapping(source = "userSettings.receiveReceipts", target = "userPreferences.receiveReceipts")
    @Mapping(source = "userSettings.receivesPromotions", target = "userPreferences.receivesPromotions")
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "avatarUrl", target = "avatarUrl")
    @Mapping(source = "hasSeenWelcome", target = "hasSeenWelcome")
    @BeanMapping(ignoreByDefault = true)
    void updateExistingUser(UpdateUserSettings userSettings, @MappingTarget User user);

    @Mapping(source = "userPreferences.receiveReceipts", target = "receiveReceipts")
    @Mapping(source = "userPreferences.receivesPromotions", target = "receivesPromotions")
    UserResponse toUserResponse(User user);

    @Mapping(target = "email", source = "oidcUser", qualifiedByName = "payloadToEmail")
    @Mapping(target = "fullName", source = "oidcUser", qualifiedByName = "payloadToFullName")
    @Mapping(target = "avatarUrl", source = "oidcUser", qualifiedByName = "payloadToAvatarUrl")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "hasSeenWelcome", ignore = true)
    @Mapping(target = "suspended", ignore = true)
    @Mapping(target = "suspensionTime", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "rentals", ignore = true)
    @Mapping(target = "userPreferences", ignore = true)
    User toNewUser(OidcUser oidcUser);

    @AfterMapping
    default void applyCreationDefaults(OidcUser oidcUser, @MappingTarget User user) {
        user.setRole(Role.CONSUMER);
        user.setHasSeenWelcome(false);
        user.setSuspended(false);
        user.setUserPreferences(new UserPreferences(true, true));
    }

    @Named("payloadToEmail")
    default String payloadToEmail(OidcUser oidcUser) {
        return oidcUser.getEmail();
    }

    @Named("payloadToFullName")
    default String payloadToFullName(OidcUser oidcUser) {
        return oidcUser.getFullName();
    }

    @Named("payloadToAvatarUrl")
    default String payloadToAvatarUrl(OidcUser oidcUser) {
        return oidcUser.getPicture();
    }
}
