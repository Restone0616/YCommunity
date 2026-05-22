package dev.restone0616.ymp.code;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64Util {
    private Base64Util() {}

    public static String encode(String plainText) {
        if (plainText == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String base64Text) {
        if (base64Text == null) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Text);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
