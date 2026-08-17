package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicPlaylistType;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.dto.music.MusicBehaviorEventRequest;
import com.example.agent.model.entity.AppUser;
import com.example.agent.repository.AppUserRepository;
import com.example.agent.service.MusicPlaylistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.agent.model.bo.MusicBehaviorEventType.PLAY_START;
import static com.example.agent.model.bo.MusicBehaviorEventType.LIKE;
import static com.example.agent.model.bo.MusicBehaviorEventType.SAVE;
import static com.example.agent.model.bo.MusicBehaviorEventType.UNLIKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MusicPlaylistIntegrationTest {
    @Autowired MusicPlaylistService playlists;
    @Autowired MusicPersonalizationRepository personalization;
    @Autowired MusicPersonalizationServiceImpl personalizationService;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private long userA;
    private long userB;
    private UUID conversationA;
    private UUID conversationB;

    @BeforeEach
    void setUp() {
        clearData();
        AppUser first = users.saveAndFlush(AppUser.register("歌单用户_A", "playlist-a@example.com", "13810000011",
                passwordEncoder.encode("Agent1234")));
        AppUser second = users.saveAndFlush(AppUser.register("歌单用户_B", "playlist-b@example.com", "13810000012",
                passwordEncoder.encode("Agent1234")));
        userA = first.getId();
        userB = second.getId();
        conversationA = UUID.randomUUID();
        conversationB = UUID.randomUUID();
        insertConversation(conversationA, userA);
        insertConversation(conversationB, userB);
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void savesRecommendationAsPlaylistAndOpensItAsFreshOwnedExposure() {
        UUID sourceExposure = expose(userA, conversationA,
                track("qq:playlist-1", "Playlist Track", "Playlist Artist"));

        var saved = playlists.createFromExposure(userA, sourceExposure, "我的专属歌单", "测试简介");

        assertThat(saved.type()).isEqualTo(MusicPlaylistType.RECOMMENDED);
        assertThat(saved.trackCount()).isEqualTo(1);
        assertThat(playlists.list(userA)).extracting(item -> item.type())
                .contains(MusicPlaylistType.FAVORITES, MusicPlaylistType.RECENT, MusicPlaylistType.RECOMMENDED);

        var opened = playlists.open(userA, saved.id(), conversationA);
        assertThat(opened.searchId()).isNotEqualTo(sourceExposure);
        assertThat(opened.tracks()).hasSize(1);
        assertThat(opened.tracks().get(0).track().name()).isEqualTo("Playlist Track");

        var event = personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), opened.searchId(), "qq:playlist-1", PLAY_START, 0L));
        assertThat(event.accepted()).isTrue();
    }

    @Test
    void rejectsCrossAccountPlaylistAndExposureAccess() {
        UUID sourceExposure = expose(userA, conversationA,
                track("qq:private", "Private Track", "Private Artist"));
        var saved = playlists.createFromExposure(userA, sourceExposure, "私有歌单", null);

        assertThatThrownBy(() -> playlists.createFromExposure(userB, sourceExposure, "伪造歌单", null))
                .isInstanceOf(AppException.class).hasMessageContaining("不属于当前用户");
        assertThatThrownBy(() -> playlists.open(userB, saved.id(), conversationB))
                .isInstanceOf(AppException.class).hasMessageContaining("不属于当前用户");
        assertThat(playlists.list(userB)).noneMatch(item -> item.name().equals("私有歌单"));
    }

    @Test
    void customPlaylistCanAddAndRemoveOnlyOwnedExposureTrack() {
        UUID sourceExposure = expose(userA, conversationA,
                track("qq:addable", "Addable Track", "Artist"));
        var custom = playlists.create(userA, "自建歌单", "可编辑");

        var withTrack = playlists.addTrack(userA, custom.id(), sourceExposure, "qq:addable");
        assertThat(withTrack.trackCount()).isEqualTo(1);
        var opened = playlists.open(userA, custom.id(), conversationA);
        long itemId = opened.tracks().get(0).playlistTrackId();
        assertThat(playlists.removeTrack(userA, custom.id(), itemId).trackCount()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_playlist_track
                 WHERE id = ? AND deleted_at IS NOT NULL
                """, Integer.class, itemId)).isEqualTo(1);

        assertThat(playlists.addTrack(userA, custom.id(), sourceExposure, "qq:addable").trackCount())
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_playlist_track
                 WHERE id = ? AND deleted_at IS NULL
                """, Integer.class, itemId)).isEqualTo(1);

        assertThatThrownBy(() -> playlists.addTrack(userB, custom.id(), sourceExposure, "qq:addable"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void deletingPlaylistKeepsAuditRowButHidesItFromUserQueries() {
        var custom = playlists.create(userA, "待删除歌单", "保留审计");

        playlists.delete(userA, custom.id());

        assertThat(playlists.list(userA)).noneMatch(item -> item.id().equals(custom.id()));
        assertThatThrownBy(() -> playlists.open(userA, custom.id(), conversationA))
                .isInstanceOf(AppException.class).hasMessageContaining("不存在");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_playlist
                 WHERE id = ? AND deleted_at IS NOT NULL
                """, Integer.class, custom.id().toString())).isEqualTo(1);
    }

    @Test
    void favoritesContainsLikesButNotBookmarkOnlyTracksAndUnlikeRemovesIt() {
        UUID likedExposure = expose(userA, conversationA,
                track("qq:favorites-liked", "Liked Song", "Artist"));
        UUID savedExposure = expose(userA, conversationA,
                track("qq:favorites-saved", "Bookmarked Song", "Artist"));

        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), likedExposure, "qq:favorites-liked", LIKE, null));
        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), savedExposure, "qq:favorites-saved", SAVE, null));

        var favorites = playlists.list(userA).stream()
                .filter(item -> item.type() == MusicPlaylistType.FAVORITES)
                .findFirst().orElseThrow();
        var opened = playlists.open(userA, favorites.id(), conversationA);
        assertThat(opened.tracks()).extracting(item -> item.track().id())
                .containsExactly("qq:favorites-liked");

        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), likedExposure, "qq:favorites-liked", UNLIKE, null));
        assertThat(playlists.open(userA, favorites.id(), conversationA).tracks()).isEmpty();
    }

    private UUID expose(long userId, UUID conversationId, MusicTrackBo track) {
        UUID exposure = UUID.randomUUID();
        var item = new MusicPersonalizationRepository.ExposureTrack(track, Map.of("catalog", 1),
                Map.of("semantic", 0.8), List.of("CATALOG_MATCH"), List.of("focused"), 0.8, false);
        personalization.recordExposure(userId, conversationId, exposure, "歌单测试推荐",
                Map.of("intent", "DISCOVERY"), "baseline-v1", MusicPersonalizationStatus.COLD_START,
                List.of(item));
        return exposure;
    }

    private void insertConversation(UUID id, long userId) {
        jdbc.update("""
                INSERT INTO agent_conversation(id, user_id, title, created_at, updated_at, is_deleted)
                VALUES (?, ?, '歌单测试', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0)
                """, id.toString(), userId);
    }

    private void clearData() {
        jdbc.update("DELETE FROM music_playlist_track");
        jdbc.update("DELETE FROM music_playlist");
        jdbc.update("DELETE FROM music_graph_outbox");
        jdbc.update("DELETE FROM music_behavior_event");
        jdbc.update("DELETE FROM music_preference_memory");
        jdbc.update("DELETE FROM music_recommendation_item");
        jdbc.update("DELETE FROM music_recommendation_exposure");
        jdbc.update("DELETE FROM music_catalog_track");
        jdbc.update("DELETE FROM music_knowledge_feedback");
        jdbc.update("DELETE FROM agent_chat_message");
        jdbc.update("DELETE FROM agent_conversation");
        jdbc.update("DELETE FROM email_verification_code");
        users.deleteAll();
    }

    private static MusicTrackBo track(String id, String name, String artist) {
        return new MusicTrackBo(id, name, List.of(artist), "Album", "https://image.example/cover.jpg",
                120_000, "https://external.example", "qq", "audio",
                "/api/music/qq/play/1", null);
    }
}
