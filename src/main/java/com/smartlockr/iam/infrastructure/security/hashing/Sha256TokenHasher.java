package com.smartlockr.iam.infrastructure.security.hashing;

import com.smartlockr.iam.domain.ports.TokenHasher;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class Sha256TokenHasher implements TokenHasher {

    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    @Override
    public String hash(String rawToken) {
        var digest = DigestUtils.sha256(rawToken);
        return BASE64_URL_ENCODER
                .encodeToString(digest);
    }

    @Override
    public boolean matches(String rawToken, String hashedToken) {
        String newHash = hash(rawToken);

        return MessageDigest.isEqual(
                newHash.getBytes(StandardCharsets.UTF_8),
                hashedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
