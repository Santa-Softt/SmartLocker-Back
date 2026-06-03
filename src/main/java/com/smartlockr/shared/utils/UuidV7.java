package com.smartlockr.shared.utils;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class UuidV7 {

    private UuidV7() {
    }

    public static UUID generate() {
        long timestampMillis = System.currentTimeMillis() & 0xffffffffffffL;
        long randomA = ThreadLocalRandom.current().nextLong() & 0x0fffl;
        long mostSignificantBits = (timestampMillis << 16) | 0x7000L | randomA;

        long randomB = ThreadLocalRandom.current().nextLong() & 0x3fffffffffffffffL;
        long leastSignificantBits = 0x8000000000000000L | randomB;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
