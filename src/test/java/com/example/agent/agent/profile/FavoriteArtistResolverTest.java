package com.example.agent.agent.profile;

import com.example.agent.agent.contract.UserTasteContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteArtistResolverTest {
    private final FavoriteArtistResolver resolver = new FavoriteArtistResolver();

    @Test
    void resolvesDominantArtistFromAuditablePlayStats() {
        var context = context(List.of(
                new UserTasteContext.RankedItem("Mili", "8 首歌曲", 12, "artist:mili"),
                new UserTasteContext.RankedItem("Aimer", "5 首歌曲", 6, "artist:aimer")));

        var result = resolver.resolve(context);

        assertThat(result.resolved()).isTrue();
        assertThat(result.artistName()).isEqualTo("Mili");
        assertThat(result.evidenceIds()).containsExactly("artist:mili");
        assertThat(result.basis()).contains("12 次", "第二名");
    }

    @Test
    void refusesToGuessWhenEvidenceCannotSeparateArtists() {
        var context = context(List.of(
                new UserTasteContext.RankedItem("Mili", "3 首歌曲", 5, "artist:mili"),
                new UserTasteContext.RankedItem("Aimer", "3 首歌曲", 5, "artist:aimer")));

        var result = resolver.resolve(context);

        assertThat(result.resolved()).isFalse();
        assertThat(result.clarification()).contains("不能可靠判断");
    }

    @Test
    void refusesToGuessFromAnEmptyProfile() {
        var result = resolver.resolve(context(List.of()));

        assertThat(result.resolved()).isFalse();
        assertThat(result.clarification()).contains("没有足够的可验证收听证据");
    }

    private static UserTasteContext context(List<UserTasteContext.RankedItem> artists) {
        long plays = artists.stream().mapToLong(UserTasteContext.RankedItem::count).sum();
        return new UserTasteContext("FORMING", "画像形成中", false, plays, plays, 0, 0,
                List.of(), List.of(), List.of(), List.of(), artists, List.of(), List.of());
    }
}
