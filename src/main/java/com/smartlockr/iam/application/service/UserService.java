package com.smartlockr.iam.application.service;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.application.mapper.UserMapper;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import com.smartlockr.shared.utils.CacheNames;
import com.smartlockr.shared.utils.UserConstraints;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    @CacheEvict(value = CacheNames.USER_CACHE, key = "#userJwt.subject")
    public UserResponse updateUserSettings(UpdateUserSettings settings, Jwt userJwt){
        if(userJwt == null)
            throw new AccessDeniedException("El usuario no tiene una sesión iniciada");

        var user = userRepository.findById(UUID.fromString(userJwt.getSubject()))
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        if (user.getRole() != Role.ADMIN)  UserConstraints.validateName(settings.fullName());

        userMapper.updateExistingUser(settings, user);

        var savedUser = userRepository.save(user);

        return userMapper.toUserResponse(savedUser);
    }

    @Cacheable(value = CacheNames.USER_CACHE, key = "#id")
    public UserResponse getUserResponse(@NotNull UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new UsernameNotFoundException("El usuario no existe"));
    }
}
