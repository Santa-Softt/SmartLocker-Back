package com.smartlockr.iam.infrastructure.security.hashing;

import com.smartlockr.iam.domain.ports.TokenHasher;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

 
/**
 * Token hashing implementation based on SHA-256.
 * The resulting hash is encoded using Base64 URL-safe encoding without padding.
 */
@Component
public class Sha256TokenHasher implements TokenHasher {

 
    /**
     * URL-safe Base64 encoder without padding, used to encode the SHA-256 digest.
     */
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

 
    /**
     * Computes the SHA-256 hash of the provided token and encodes it as Base64 URL-safe.
     * @param rawToken plain token
     * @return Base64 URL-safe SHA-256 hash
     */
    @Override
    public String hash(String rawToken) {
        var digest = DigestUtils.sha256(rawToken);
        return BASE64_URL_ENCODER
                .encodeToString(digest);
    }

 
    /**
     * Compares a raw token with a previously hashed token.
     * This method hashes {@code rawToken} and performs a constant-time equality check.
     * @param rawToken plain token
     * @param hashedToken expected hash
     * @return true if the computed hash matches the provided hash
     */
    @Override
    public boolean matches(String rawToken, String hashedToken) {
        String newHash = hash(rawToken);

        return MessageDigest.isEqual(
                newHash.getBytes(StandardCharsets.UTF_8),
                hashedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
