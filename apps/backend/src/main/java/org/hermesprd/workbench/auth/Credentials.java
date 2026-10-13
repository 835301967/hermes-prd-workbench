package org.hermesprd.workbench.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class Credentials {
    private static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Credentials() {
    }

    public static String hashPassword(String raw) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password exceeds the supported UTF-8 length");
        }
        return PASSWORDS.encode(raw);
    }

    public static boolean passwordMatches(String raw, String hashed) {
        return raw != null && hashed != null
                && raw.getBytes(StandardCharsets.UTF_8).length <= 72
                && PASSWORDS.matches(raw, hashed);
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
