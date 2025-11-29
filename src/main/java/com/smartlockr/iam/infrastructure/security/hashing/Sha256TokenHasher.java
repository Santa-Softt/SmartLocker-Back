package com.smartlockr.iam.infrastructure.security.hashing;

import com.smartlockr.iam.domain.ports.TokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class Sha256TokenHasher implements TokenHasher {

    private static final String ALGORITHM = "SHA-256";

    @Override
    public String hash(String rawToken) {
        return encode(digest(rawToken));
    }

    @Override
    public boolean matches(String rawToken, String hashedToken) {
        // Hasheamos el token entrante
        String newHash = hash(rawToken);

        // Comparamos de forma segura (Constant Time)
        // Evitamos ataques de tiempo comparando byte a byte sin detenerse en el primer error.
        return MessageDigest.isEqual(
                newHash.getBytes(StandardCharsets.UTF_8),
                hashedToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] digest(String raw) {
        try {
            return MessageDigest.getInstance(ALGORITHM)
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hashing algorithm not available: " + ALGORITHM, e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
