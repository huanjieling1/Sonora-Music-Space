package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.model.bo.MusicCatalogSearchType;
import com.example.agent.model.bo.MusicPlaylistBo;
import com.example.agent.model.bo.MusicPlaylistType;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.MusicCatalogProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicCatalogSearchServiceImplTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void returnsTracksArtistsLocalAndOnlinePlaylistsWithoutGeneratingAPersistedPlaylist() {
        MusicTrackBo track = new MusicTrackBo("qq:track-1", "温柔", List.of("五月天"), "爱情万岁",
                "https://image.test/cover.jpg", 240_000, "https://source.test", "qq", "audio",
                "/api/music/qq/play/track1", null);
        MusicCatalogProvider provider = provider(track);
        MusicPersonalizationRepository personalization = mock(MusicPersonalizationRepository.class);
        MusicPlaylistRepository playlists = mock(MusicPlaylistRepository.class);
        UUID localId = UUID.randomUUID();
        when(playlists.list(7L)).thenReturn(List.of(new MusicPlaylistBo(localId, MusicPlaylistType.CUSTOM,
                "五月天收藏", "我的现场歌单", track.imageUrl(), 12, true, LocalDateTime.now())));
        var service = new MusicCatalogSearchServiceImpl(List.of(provider), executor, properties(),
                personalization, playlists);
        UUID conversationId = UUID.randomUUID();

        var result = service.search(7L, conversationId, "五月天", MusicCatalogSearchType.ALL, 1, 20);

        assertThat(result.tracks()).singleElement().extracting(MusicTrackBo::name).isEqualTo("温柔");
        assertThat(result.artists()).singleElement().satisfies(artist -> {
            assertThat(artist.name()).isEqualTo("五月天");
            assertThat(artist.matchedTracks()).isEqualTo(1);
        });
        assertThat(result.playlists()).hasSize(2);
        assertThat(result.playlists()).filteredOn(playlist -> playlist.local()).singleElement()
                .extracting(playlist -> playlist.id()).isEqualTo(localId.toString());
        assertThat(result.playlists()).filteredOn(playlist -> !playlist.local()).singleElement()
                .satisfies(playlist -> assertThat(playlist.tracks()).containsExactly(track));
        verify(personalization).recordExposure(eq(7L), eq(conversationId), eq(result.searchId()),
                eq("曲库搜索：五月天"), any(), eq("catalog-search-v1"), any(), any());
    }

    @Test
    void searchesGenreAliasesIndependently() {
        MusicPersonalizationRepository personalization = mock(MusicPersonalizationRepository.class);
        MusicPlaylistRepository playlists = mock(MusicPlaylistRepository.class);
        when(playlists.list(anyLong())).thenReturn(List.of());
        var service = new MusicCatalogSearchServiceImpl(List.of(provider()), executor, properties(),
                personalization, playlists);

        var result = service.search(9L, UUID.randomUUID(), "独立摇滚",
                MusicCatalogSearchType.GENRE, 1, 20);

        assertThat(result.genres()).singleElement().satisfies(genre -> {
            assertThat(genre.id()).isEqualTo("rock");
            assertThat(genre.searchQuery()).isEqualTo("摇滚");
        });
    }

    private static MusicCatalogProvider provider(MusicTrackBo... tracks) {
        return new MusicCatalogProvider() {
            public String id() { return "qq"; }
            public String displayName() { return "QQ 音乐"; }
            public boolean configured() { return true; }
            public boolean fallbackOnly() { return false; }
            public int order() { return 1; }
            public List<String> playbackTypes() { return List.of("audio"); }
            public List<MusicTrackBo> search(String query, int limit) { return List.of(tracks); }
            public List<MusicTrackBo> search(String query, int page, int pageSize) { return List.of(tracks); }
        };
    }

    private static MusicCatalogProperties properties() {
        return new MusicCatalogProperties(5,
                new MusicCatalogProperties.Jamendo("", "https://jamendo.test"),
                new MusicCatalogProperties.Audius("", "https://audius.test"),
                new MusicCatalogProperties.Youtube("", "https://youtube.test"),
                new MusicCatalogProperties.Qq(true, "http://qq.test", "runtime-data", "flac"));
    }
}
