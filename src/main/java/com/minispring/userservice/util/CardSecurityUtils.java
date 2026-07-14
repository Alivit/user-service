package com.minispring.userservice.util;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CardSecurityUtils {

    @Value("${app.security.card-pepper}")
    private String pepper;

    @Named("HashCard")
    public String hash(String rawNumber) {
        if (rawNumber == null) return null;
        String cleanNumber = rawNumber.replaceAll("\\D", "");

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hashBytes = mac.doFinal(cleanNumber.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash payment card", e);
        }
    }

    @Named("MaskCard")
    public String mask(String rawNumber) {
        if (rawNumber == null) return null;
        String cleanNumber = rawNumber.replaceAll("\\D", "");

        if (cleanNumber.length() < 4) return cleanNumber;
        return "**** **** **** " + cleanNumber.substring(cleanNumber.length() - 4);
    }
}
