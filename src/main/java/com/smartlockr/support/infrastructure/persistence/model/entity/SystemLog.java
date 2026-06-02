package com.smartlockr.support.infrastructure.persistence.model.entity;

import com.smartlockr.iam.infrastructure.persistence.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_logs", indexes = {
        @Index(name = "idx_system_log_user_id", columnList = "user_id"),
        @Index(name = "idx_system_log_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_system_log_created_at", columnList = "created_at")
})
public class SystemLog {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String resourceType;

    @Column(length = 80)
    private String resourceId;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
