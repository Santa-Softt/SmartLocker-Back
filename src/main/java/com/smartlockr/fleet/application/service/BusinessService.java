package com.smartlockr.fleet.application.service;

import com.smartlockr.fleet.application.exception.MissingBusinessConfigException;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessService {
    private final BusinessConfigRepository businessConfigRepository;

    @Transactional(readOnly = true)
    public BusinessConfig getActiveBusinessConfig() {
        return businessConfigRepository.findTheOne()
                .orElseThrow(() -> new MissingBusinessConfigException("Critical: Business configuration not found in the database."));
    }
}
