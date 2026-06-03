package com.smartlockr.shared.infrastructure.persistence;

import com.smartlockr.shared.utils.UuidV7;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;

import java.util.UUID;

public class UuidV7ValueGenerator implements UuidValueGenerator {

    @Override
    public UUID generateUuid(SharedSessionContractImplementor session) {
        return UuidV7.generate();
    }
}
