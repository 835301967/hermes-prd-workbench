package org.hermesprd.workbench.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialsTest {
    @Test
    void rejectsOverlongPasswordWithoutTruncatingAsciiOrUtf8() {
        String ascii = "a".repeat(72);
        String chinese = "密".repeat(24);
        assertTrue(Credentials.passwordMatches(ascii, Credentials.hashPassword(ascii)));
        assertFalse(Credentials.passwordMatches(ascii + "x", Credentials.hashPassword(ascii)));
        assertTrue(Credentials.passwordMatches(chinese, Credentials.hashPassword(chinese)));
        assertFalse(Credentials.passwordMatches(chinese + "码", Credentials.hashPassword(chinese)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Credentials.hashPassword(chinese + "码"));
    }

    @Test
    void passwordHashRoundTrip() {
        String hash = Credentials.hashPassword("secret-pass");
        assertTrue(Credentials.passwordMatches("secret-pass", hash));
        assertFalse(Credentials.passwordMatches("wrong-pass", hash));
    }

    @Test
    void tokenHashIsStableAndNotRaw() {
        String token = Credentials.newToken();
        String hashed = Credentials.hashToken(token);
        assertNotEquals(token, hashed);
        assertTrue(hashed.matches("[0-9a-f]{64}"));
        org.junit.jupiter.api.Assertions.assertEquals(hashed, Credentials.hashToken(token));
    }
}
