package com.example.agent.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class Sha256PasswordEncoder implements PasswordEncoder {
    private static final String PREFIX = "sha256";
    private static final int SALT_BYTES = 16;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int iterations;

    public Sha256PasswordEncoder(int iterations) {
        if (iterations < 10_000) {
            throw new IllegalArgumentException("SHA-256 iterations must be at least 10000");
        }
        this.iterations = iterations;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] digest = digest(rawPassword, salt, iterations);
        return String.join("$", PREFIX, Integer.toString(iterations),
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(digest));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        try {
            String[] parts = encodedPassword.split("\\$", -1);
            if (parts.length != 4 || !PREFIX.equals(parts[0])) {
                return false;
            }
            int storedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, digest(rawPassword, salt, storedIterations));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        try {
            String[] parts = encodedPassword.split("\\$", -1);
            return parts.length != 4 || Integer.parseInt(parts[1]) < iterations;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static byte[] digest(CharSequence password, byte[] salt, int rounds) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] value = password.toString().getBytes(StandardCharsets.UTF_8);
            messageDigest.update(salt);
            value = messageDigest.digest(value);
            for (int i = 1; i < rounds; i++) {
                messageDigest.reset();
                messageDigest.update(salt);
                value = messageDigest.digest(value);
            }
            return value;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
