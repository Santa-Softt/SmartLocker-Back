package com.smartlockr.billing.application.service;

import com.smartlockr.shared.properties.MercadoPagoProperties;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoSignatureValidatorTest {

    private final MercadoPagoSignatureValidator validator = new MercadoPagoSignatureValidator(
            new MercadoPagoProperties(
                    "access-token",
                    "secret",
                    "https://api.test/webhook",
                    "https://app.test/success",
                    "https://app.test/failure"
            )
    );

    @Test
    @DisplayName("isValid - accepts valid MercadoPago HMAC signatures")
    void shouldAcceptValidSignature() {
        String requestId = "request-1";
        String resourceId = "12345";
        String ts = "1717350000";
        String manifest = "id:%s;request-id:%s;ts:%s;".formatted(resourceId, requestId, ts);
        String signature = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, "secret").hmacHex(manifest);

        assertThat(validator.isValid("ts=%s,v1=%s".formatted(ts, signature), requestId, resourceId)).isTrue();
    }

    @Test
    @DisplayName("isValid - rejects invalid signatures")
    void shouldRejectInvalidSignature() {
        assertThat(validator.isValid("ts=1717350000,v1=invalid", "request-1", "12345")).isFalse();
    }

    @Test
    @DisplayName("isValid - rejects malformed headers")
    void shouldRejectMalformedHeaders() {
        assertThat(validator.isValid(null, "request-1", "12345")).isFalse();
        assertThat(validator.isValid("ts=1717350000", "request-1", "12345")).isFalse();
        assertThat(validator.isValid("broken", "request-1", "12345")).isFalse();
    }
}
