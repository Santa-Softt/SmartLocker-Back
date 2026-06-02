package com.smartlockr.billing.application.service;

import com.smartlockr.shared.properties.MercadoPagoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoPagoSignatureValidator {
    private final MercadoPagoProperties mpProperties;

    /**
     * Validates the authenticity of the webhook.
     *
     * @param xSignature The raw x-signature header.
     * @param xRequestId The raw x-request-id header.
     * @param resourceId The ID from the 'data' object (data.id).
     * @return true if the signature is valid.
     */
    public boolean isValid(String xSignature, String xRequestId, String resourceId) {
        return parseHeader(xSignature).map(header -> {
            String manifest = "id:%s;request-id:%s;ts:%s;".formatted(resourceId, xRequestId, header.ts());

            log.debug("[MERCADOPAGO] Validating with manifest: {}", manifest);

            String secret = mpProperties.webhookSecret().trim();
            HmacUtils hmac = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret);
            String manifestHash = hmac.hmacHex(manifest);

            byte[] expectedHashBytes = header.v1().getBytes(StandardCharsets.UTF_8);
            byte[] calculatedHashBytes = manifestHash.getBytes(StandardCharsets.UTF_8);

            boolean matches = MessageDigest.isEqual(calculatedHashBytes, expectedHashBytes);

            if (!matches) {
                log.error("[MERCADOPAGO] Signature Mismatch! Expected: {}, Calculated: {}", header.v1(), manifestHash);
            }

            return matches;
        }).orElse(false);
    }

    private Optional<SignatureData> parseHeader(String headerValue) {
        if (headerValue == null || !headerValue.contains(",")) return Optional.empty();

        try {
            Map<String, String> parts = Arrays.stream(headerValue.split(","))
                    .map(part -> part.split("=", 2))
                    .filter(pair -> pair.length == 2)
                    .collect(Collectors.toMap(
                            pair -> pair[0].trim(),
                            pair -> pair[1].trim(),
                            (existing, _) -> existing
                    ));

            return (parts.containsKey("ts") && parts.containsKey("v1"))
                    ? Optional.of(new SignatureData(parts.get("ts"), parts.get("v1")))
                    : Optional.empty();
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private record SignatureData(String ts, String v1) {}
}
