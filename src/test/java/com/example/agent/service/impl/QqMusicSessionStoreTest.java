package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QqMusicSessionStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void encryptsPersistsRestoresAndClearsCookie() throws Exception {
        String cookie = "uin=o12345678; qm_keyst=private-value";
        var properties = properties(temporaryDirectory);
        var store = new QqMusicSessionStore(properties);

        store.save(cookie);

        assertThat(store.hasSession()).isTrue();
        assertThat(store.maskedAccount()).endsWith("5678").doesNotContain("1234");
        assertThat(Files.readString(temporaryDirectory.resolve("qq-session.dat")))
                .doesNotContain("private-value").doesNotContain("12345678");

        var restored = new QqMusicSessionStore(properties);
        restored.load();
        assertThat(restored.cookie()).contains(cookie);

        restored.clear();
        assertThat(restored.hasSession()).isFalse();
        assertThat(temporaryDirectory.resolve("qq-session.dat")).doesNotExist();
    }

    private static MusicCatalogProperties properties(Path directory) {
        return new MusicCatalogProperties(5,
                new MusicCatalogProperties.Jamendo("", "https://jamendo.test"),
                new MusicCatalogProperties.Audius("", "https://audius.test"),
                new MusicCatalogProperties.Youtube("", "https://youtube.test"),
                new MusicCatalogProperties.Qq(true, "http://127.0.0.1:3200", directory.toString(), "flac"));
    }
}
