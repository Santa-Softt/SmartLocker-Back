package com.smartlockr.iam.application.service;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.application.mapper.UserMapper;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import com.smartlockr.shared.utils.UrlConstraints;
import com.smartlockr.shared.utils.UserConstraints;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse updateUserSettings(UpdateUserSettings settings, Jwt userJwt){
        if(userJwt == null)
            throw new AccessDeniedException("El usuario no tiene una sesión iniciada");

        UserConstraints.validateName(settings.fullName());
        UrlConstraints.validateUrl(settings.avatarUrl());

        var user = userRepository.findById(UUID.fromString(userJwt.getSubject()))
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        userMapper.updateExistingUser(settings, user);

        var savedUser = userRepository.save(user);

        return userMapper.toUserResponse(savedUser);
    }
}
