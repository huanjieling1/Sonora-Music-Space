package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicTrackBo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

final class MusicTrackIdentity {
    private MusicTrackIdentity() {
    }

    static String key(MusicTrackBo track) {
        return sha256(normalize(track.provider()) + "\0" + normalize(track.id()));
    }

    /** Cross-provider identity used only for recommendation novelty and duplicate suppression. */
    static String canonicalKey(MusicTrackBo track) {
        String artist = track.artists() == null || track.artists().isEmpty() ? "" : track.artists().get(0);
        return canonicalKey(track.name(), artist);
    }

    static String canonicalKey(String title, String primaryArtist) {
        return sha256(MusicTextNormalizer.normalize(title) + "\0"
                + MusicTextNormalizer.normalize(primaryArtist));
    }

    static String contentText(MusicTrackBo track, Iterable<String> tags) {
        StringBuilder text = new StringBuilder();
        append(text, track.name());
        if (track.artists() != null) {
            track.artists().forEach(value -> append(text, value));
        }
        append(text, track.album());
        append(text, track.relationLabel());
        if (tags != null) {
            tags.forEach(value -> append(text, value));
        }
        return text.toString().strip();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).strip();
    }

    private static void append(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) {
            if (!target.isEmpty()) target.append("; ");
            target.append(value.strip());
        }
    }
}
