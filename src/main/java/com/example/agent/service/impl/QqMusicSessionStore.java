package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QqMusicSessionStore {
    private static final Logger log = LoggerFactory.getLogger(QqMusicSessionStore.class);
    private static final Pattern UIN_PATTERN = Pattern.compile("(?:^|;\\s*)(?:uin|wxuin)=([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_COOKIE_LENGTH = 16_384;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private final Path directory;
    private final Path keyFile;
    private final Path sessionFile;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile String cookie;

    public QqMusicSessionStore(MusicCatalogProperties properties) {
        String configuredDirectory = properties.qq() == null ? "runtime-data" : properties.qq().sessionDirectory();
        this.directory = Path.of(StringUtils.hasText(configuredDirectory) ? configuredDirectory : "runtime-data")
                .toAbsolutePath().normalize();
        this.keyFile = directory.resolve("qq-session.key");
        this.sessionFile = directory.resolve("qq-session.dat");
    }

    @PostConstruct
    void load() {
        if (!Files.isRegularFile(keyFile) || !Files.isRegularFile(sessionFile)) {
            return;
        }
        try {
            byte[] key = Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.UTF_8).trim());
            byte[] encrypted = Base64.getDecoder().decode(Files.readString(sessionFile, StandardCharsets.UTF_8).trim());
            if (encrypted.length <= IV_LENGTH) {
                throw new IllegalStateException("encrypted session is incomplete");
            }
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            String restored = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            validate(restored);
            cookie = restored;
        } catch (Exception exception) {
            cookie = null;
            log.warn("Unable to restore QQ Music session: {}", exception.getClass().getSimpleName());
        }
    }

    public synchronized void save(String value) {
        String normalized = value == null ? "" : value.trim();
        validate(normalized);
        try {
            Files.createDirectories(directory);
            SecretKey key = loadOrCreateKey();
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext);
            writeAtomically(sessionFile, Base64.getEncoder().encodeToString(payload.array()));
            cookie = normalized;
        } catch (Exception exception) {
            throw new IllegalStateException("无法安全保存 QQ 音乐登录态", exception);
        }
    }

    public synchronized void clear() {
        cookie = null;
        try {
            Files.deleteIfExists(sessionFile);
        } catch (Exception exception) {
            throw new IllegalStateException("无法清除 QQ 音乐登录态", exception);
        }
    }

    public Optional<String> cookie() {
        return Optional.ofNullable(cookie).filter(StringUtils::hasText);
    }

    public boolean hasSession() {
        return cookie().isPresent();
    }

    public String maskedAccount() {
        if (!hasSession()) {
            return "";
        }
        Matcher matcher = UIN_PATTERN.matcher(cookie);
        if (!matcher.find()) {
            return "已导入";
        }
        String raw = matcher.group(1).replaceFirst("^[oO]", "");
        if (raw.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.min(8, raw.length() - 4)) + raw.substring(raw.length() - 4);
    }

    private void validate(String value) {
        if (!StringUtils.hasText(value) || value.length() > MAX_COOKIE_LENGTH || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0 || !value.contains("=")) {
            throw new IllegalArgumentException("QQ 音乐 Cookie 格式不正确");
        }
    }

    private SecretKey loadOrCreateKey() throws Exception {
        if (Files.isRegularFile(keyFile)) {
            return new SecretKeySpec(Base64.getDecoder().decode(
                    Files.readString(keyFile, StandardCharsets.UTF_8).trim()), "AES");
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, secureRandom);
        SecretKey key = generator.generateKey();
        writeAtomically(keyFile, Base64.getEncoder().encodeToString(key.getEncoded()));
        return key;
    }

    private static void writeAtomically(Path target, String content) throws Exception {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
