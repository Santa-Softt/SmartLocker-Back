package com.smartlockr.support.application.service;

import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.support.infrastructure.persistence.model.entity.SystemLog;
import com.smartlockr.support.infrastructure.persistence.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final SystemLogRepository systemLogRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User user, String action, String resourceType, String resourceId, String details) {
        SystemLog log = SystemLog.builder()
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .build();

        systemLogRepository.save(log);
    }
}
