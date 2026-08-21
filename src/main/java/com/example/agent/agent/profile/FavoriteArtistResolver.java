package com.example.agent.agent.profile;

import com.example.agent.agent.contract.FavoriteArtistResolution;
import com.example.agent.agent.contract.UserTasteContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Deterministically binds an artist entity from auditable profile evidence; it never guesses from prose. */
@Component
public final class FavoriteArtistResolver {
    private static final double MIN_EXPLICIT_CONFIDENCE = 0.65;
    private static final long MIN_PLAY_COUNT = 3;
    private static final double MIN_DOMINANCE = 1.25;

    public FavoriteArtistResolution resolve(UserTasteContext context) {
        if (context == null || !context.hasEvidence()) {
            return insufficient();
        }

        UserTasteContext.Signal explicit = context.likes().stream()
                .filter(value -> "ARTIST".equals(value.type().toUpperCase(Locale.ROOT)))
                .filter(value -> value.confidence() >= MIN_EXPLICIT_CONFIDENCE)
                .max(Comparator.comparingDouble(UserTasteContext.Signal::confidence))
                .orElse(null);
        if (explicit != null) {
            return new FavoriteArtistResolution(true, explicit.value(), explicit.basis(), explicit.confidence(),
                    List.of(explicit.evidenceId()), "");
        }

        if (context.topArtists().isEmpty()) return insufficient();
        UserTasteContext.RankedItem first = context.topArtists().get(0);
        long secondCount = context.topArtists().size() > 1 ? context.topArtists().get(1).count() : 0;
        double dominance = secondCount <= 0 ? first.count() : (double) first.count() / secondCount;
        if (first.count() < MIN_PLAY_COUNT || dominance < MIN_DOMINANCE) {
            return FavoriteArtistResolution.unresolved(
                    "我能看到你的收听画像，但目前还不能可靠判断唯一最喜欢的歌手。"
                            + "你可以直接告诉我歌手名字，或再积累一些播放、喜欢和收藏记录。");
        }

        double confidence = Math.min(0.92, 0.58 + Math.min(0.2, first.count() * 0.02)
                + Math.min(0.14, Math.max(0, dominance - 1) * 0.1));
        String basis = "画像中收听次数最高，共 " + first.count() + " 次"
                + (secondCount > 0 ? "，约为第二名的 " + String.format(Locale.ROOT, "%.1f", dominance) + " 倍" : "");
        return new FavoriteArtistResolution(true, first.name(), basis, confidence,
                List.of(first.evidenceId()), "");
    }

    private static FavoriteArtistResolution insufficient() {
        return FavoriteArtistResolution.unresolved(
                "我目前没有足够的可验证收听证据来判断你最喜欢的歌手。"
                        + "请告诉我歌手名字，或先播放、喜欢、收藏一些歌曲后再试。");
    }
}
