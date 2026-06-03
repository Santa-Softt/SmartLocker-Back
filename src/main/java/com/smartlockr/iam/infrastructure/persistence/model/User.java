package com.smartlockr.iam.infrastructure.persistence.model;

import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import com.smartlockr.shared.infrastructure.persistence.UuidV7ValueGenerator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.*;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "Users",
        indexes = @Index(name = "idx_email", columnList = "email"))
@Entity
@Getter
@Setter
@Builder
@EqualsAndHashCode(of = "id")
public class User {
    @Id
    @UuidGenerator(algorithm = UuidV7ValueGenerator.class)
    private UUID id;

    @Column(name= "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private boolean hasSeenWelcome;

    @Column(nullable = false)
    private boolean suspended;

    private Instant suspensionTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private Role role;

    @Version
    private Long version;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private Set<RefreshToken> refreshTokens = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Rental> rentals;

    @Embedded
    private UserPreferences userPreferences;

}
