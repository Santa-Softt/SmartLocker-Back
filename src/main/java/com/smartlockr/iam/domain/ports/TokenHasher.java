package com.smartlockr.iam.domain.ports;

public interface TokenHasher {
    String hash(String raw);
    boolean matches(String raw, String hashed);
}
