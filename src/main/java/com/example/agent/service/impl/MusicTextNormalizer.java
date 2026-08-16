package com.example.agent.service.impl;

import java.text.Normalizer;
import java.util.Locale;

final class MusicTextNormalizer {
    private MusicTextNormalizer() {
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static String cleanRequest(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip().replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ");
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    static String entityCandidate(String value) {
        String result = cleanRequest(value)
                .replaceFirst("(?i)^(?:请|帮我|我想|想要|给我|我是说)?\\s*(?:播放|听听|听|找找|找|搜索|推荐)?\\s*", "")
                .replaceFirst("(?:的歌|的歌曲|的音乐|相关音乐|相关歌曲|主题曲|原声带|原声|歌曲|音乐)$", "")
                .strip();
        return result.length() > 120 ? result.substring(0, 120) : result;
    }

    static String primarySearchQuery(String value) {
        String original = cleanRequest(value);
        String result = original
                .replaceFirst("^(?:请|麻烦(?:你)?|劳驾)\\s*", "")
                .replaceFirst("^(?:帮我|给我)\\s*", "")
                .replaceFirst("^(?:我是说|我是指|我说的是)\\s*", "")
                .replaceFirst("^我\\s*", "")
                .replaceFirst("^(?:想要|想|希望|需要)\\s*", "")
                .replaceFirst("^(?:找到|找找|找|搜索|搜搜|搜|推荐|播放|听听|听)\\s*", "")
                .replaceFirst("^(?:一下|一些|一首|一曲)\\s*", "")
                .replaceFirst("(?:的)?(?:相关)?(?:歌曲|音乐|歌|曲目)$", "")
                .replaceFirst("的$", "")
                .strip();
        if (result.length() < 2) {
            return original;
        }
        return result.length() > 100 ? result.substring(0, 100).strip() : result;
    }
}
