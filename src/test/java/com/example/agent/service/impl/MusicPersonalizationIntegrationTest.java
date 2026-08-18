package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicBehaviorEventType;
import com.example.agent.model.bo.MusicFeedbackAction;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.dto.music.MusicBehaviorEventRequest;
import com.example.agent.model.dto.music.MusicFeedbackRequest;
import com.example.agent.model.dto.music.MusicPreferenceRequest;
import com.example.agent.model.entity.AppUser;
import com.example.agent.repository.AppUserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MusicPersonalizationIntegrationTest {
    @Autowired MusicPersonalizationRepository repository;
    @Autowired MusicPersonalizationServiceImpl personalizationService;
    @Autowired MusicFeedbackServiceImpl feedbackService;
    @Autowired MusicMemoryConsolidator consolidator;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private long userA;
    private long userB;
    private UUID conversationA;
    private UUID conversationB;

    @BeforeEach
    void setUp() {
        clearPersonalizationData();
        AppUser first = users.saveAndFlush(AppUser.register("推荐用户_A", "music-a@example.com", "13810000001",
                passwordEncoder.encode("Agent1234")));
        AppUser second = users.saveAndFlush(AppUser.register("推荐用户_B", "music-b@example.com", "13810000002",
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
        clearPersonalizationData();
    }

    @Test
    void acceptsOnlyOwnedExposureItemsAndDeduplicatesEventIds() {
        UUID exposure = expose(userA, conversationA, track("qq:owned", "Owned Track", "Artist A"));
        UUID eventId = UUID.randomUUID();
        MusicBehaviorEventRequest like = new MusicBehaviorEventRequest(
                eventId, exposure, "qq:owned", MusicBehaviorEventType.LIKE, null);

        assertThat(personalizationService.recordEvent(userA, like).duplicate()).isFalse();
        assertThat(personalizationService.recordEvent(userA, like).duplicate()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM music_behavior_event WHERE event_id = ?",
                Integer.class, eventId.toString())).isEqualTo(1);

        assertThatThrownBy(() -> personalizationService.recordEvent(userB,
                new MusicBehaviorEventRequest(UUID.randomUUID(), exposure, "qq:owned",
                        MusicBehaviorEventType.LIKE, null)))
                .isInstanceOf(AppException.class).hasMessageContaining("不属于当前用户");
        assertThatThrownBy(() -> personalizationService.recordEvent(userA,
                new MusicBehaviorEventRequest(UUID.randomUUID(), exposure, "qq:forged",
                        MusicBehaviorEventType.LIKE, null)))
                .isInstanceOf(AppException.class).hasMessageContaining("不属于当前用户");
        assertThatThrownBy(() -> personalizationService.recordEvent(userA,
                new MusicBehaviorEventRequest(UUID.randomUUID(), exposure, "qq:owned",
                        MusicBehaviorEventType.SKIP, 1_000L)))
                .isInstanceOf(AppException.class).hasMessageContaining("不足 2 秒");
    }

    @Test
    void persistsBatchSequenceAndRestoresRecentExposureTracksForRefresh() {
        expose(userA, conversationA, track("qq:batch:1", "第一批歌曲", "歌手甲"));
        expose(userA, conversationA, track("qq:batch:2", "第二批歌曲", "歌手乙"));
        String fingerprint = MusicTrackIdentity.sha256(MusicTextNormalizer.normalize("测试推荐"));

        assertThat(repository.nextBatchSequence(userA, conversationA, fingerprint)).isEqualTo(3);
        assertThat(repository.recentExposureTracks(userA, conversationA, 6))
                .extracting(MusicPersonalizationRepository.RecentExposureTrack::title)
                .containsExactlyInAnyOrder("第二批歌曲", "第一批歌曲");
        assertThat(jdbc.queryForList("""
                SELECT refresh_source FROM music_recommendation_exposure
                 WHERE user_id = ? AND conversation_id = ? ORDER BY created_at
                """, String.class, userA, conversationA.toString()))
                .containsExactly("STANDARD", "STANDARD");
    }

    @Test
    void unlikeOnlyRemovesTheLikeAndNeverCreatesNegativePreference() {
        UUID exposure = expose(userA, conversationA, track("qq:liked", "Liked Track", "Artist A"));

        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), exposure, "qq:liked", MusicBehaviorEventType.LIKE, null));
        assertThat(personalizationService.isTrackLiked(userA, exposure, "qq:liked")).isTrue();

        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), exposure, "qq:liked", MusicBehaviorEventType.UNLIKE, null));

        assertThat(personalizationService.isTrackLiked(userA, exposure, "qq:liked")).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_preference_memory
                 WHERE user_id = ? AND normalized_value = 'qq:liked'
                   AND polarity = -1 AND deleted_at IS NULL
                """, Integer.class, userA)).isZero();
    }

    @Test
    void contextualFeedbackAndEditableProfileStayIsolatedByUser() {
        UUID exposure = expose(userA, conversationA, track("qq:scene", "Scene Track", "Artist A"));
        feedbackService.record(userA, new MusicFeedbackRequest(exposure, conversationA,
                MusicFeedbackAction.NOT_RELEVANT, "不符合当前专注场景", "qq:scene", "coding",
                null, null));

        assertThat(repository.recentContextRejections(userA, conversationA)).containsExactly("qq:scene");
        assertThat(repository.recentContextRejections(userB, conversationB)).isEmpty();
        assertThatThrownBy(() -> feedbackService.record(userB, new MusicFeedbackRequest(
                exposure, conversationA, MusicFeedbackAction.NOT_RELEVANT, "伪造反馈", "qq:scene",
                "coding", null, null))).isInstanceOf(AppException.class);

        var preference = personalizationService.addPreference(userA,
                new MusicPreferenceRequest(MusicPreferenceType.GENRE, "post-rock", 1));
        assertThat(personalizationService.profile(userA).explicitPreferences()).hasSize(1);
        assertThat(personalizationService.profile(userB).explicitPreferences()).isEmpty();
        assertThat(personalizationService.profile(userA).summary().likes())
                .extracting(item -> item.value()).containsExactly("post-rock");
        assertThat(personalizationService.profile(userB).summary().likes()).isEmpty();
        assertThatThrownBy(() -> personalizationService.deletePreference(userB, preference.id()))
                .isInstanceOf(AppException.class).hasMessageContaining("不属于当前用户");
    }

    @Test
    void infersL2OnlyAfterThreeEventsAcrossTwoExposuresAndCanClearIt() {
        MusicTrackBo track = track("qq:learned", "Learned Track", "Learned Artist");
        UUID first = expose(userA, conversationA, track);
        UUID second = expose(userA, conversationA, track);
        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), first, track.id(), MusicBehaviorEventType.COMPLETE, 110_000L));
        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), first, track.id(), MusicBehaviorEventType.LIKE, null));

        consolidator.consolidate();
        assertThat(personalizationService.profile(userA).inferredPreferences()).isEmpty();

        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), second, track.id(), MusicBehaviorEventType.COMPLETE, 110_000L));
        consolidator.consolidate();

        var inferred = personalizationService.profile(userA).inferredPreferences();
        assertThat(inferred).extracting(item -> item.value()).contains("Learned Artist", "focused");
        assertThat(inferred).allSatisfy(item -> {
            assertThat(item.evidenceCount()).isGreaterThanOrEqualTo(3);
            assertThat(item.confidence()).isGreaterThanOrEqualTo(0.70);
            assertThat(item.expiresAt()).isNotNull();
        });
        assertThat(personalizationService.clearLearned(userA)).isGreaterThanOrEqualTo(2);
        assertThat(personalizationService.profile(userA).inferredPreferences()).isEmpty();
    }

    @Test
    void outboxFailureIsRetriableWithoutLosingMysqlFacts() {
        MusicTrackBo retryTrack = track("qq:retry", "Retry Track", "Artist A");
        expose(userA, conversationA, retryTrack);
        expose(userA, conversationA, retryTrack);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_graph_outbox
                 WHERE event_type = 'UPSERT_SONG' AND status IN ('PENDING', 'RETRY')
                """, Integer.class)).isEqualTo(1);
        var pending = repository.pendingOutbox(10);
        assertThat(pending).isNotEmpty();

        repository.markOutboxRetry(pending.get(0).id(), 1, "Neo4j unavailable");

        Map<String, Object> state = jdbc.queryForMap(
                "SELECT status, attempts, last_error FROM music_graph_outbox WHERE id = ?", pending.get(0).id());
        assertThat(state.get("status")).isEqualTo("RETRY");
        assertThat(((Number) state.get("attempts")).intValue()).isEqualTo(1);
        assertThat(state.get("last_error")).isEqualTo("Neo4j unavailable");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM music_recommendation_exposure",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void purgesL0EventsAndUnreferencedExposuresAfterOneHundredEightyDays() {
        UUID exposure = expose(userA, conversationA, track("qq:expired", "Expired Track", "Artist A"));
        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), exposure, "qq:expired", MusicBehaviorEventType.PLAY_START, 0L));
        jdbc.update("UPDATE music_behavior_event SET created_at = CURRENT_TIMESTAMP(6) - INTERVAL 181 DAY");
        jdbc.update("UPDATE music_recommendation_exposure SET created_at = CURRENT_TIMESTAMP(6) - INTERVAL 181 DAY");

        var result = repository.purgeExpiredOperationalData();

        assertThat(result.events()).isEqualTo(1);
        assertThat(result.exposures()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM music_recommendation_item", Integer.class))
                .isZero();
    }

    @Test
    void aggregatesOnePlaybackSessionAcrossProgressUpdatesWithoutDoubleCounting() {
        MusicTrackBo track = track("qq:session", "Session Track", "Artist A");
        UUID exposure = expose(userA, conversationA, track);
        UUID session = UUID.randomUUID();

        assertThat(personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), session, exposure, track.id(), MusicBehaviorEventType.PLAY_START, 0L, 0L))
                .duplicate()).isFalse();
        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), session, exposure, track.id(), MusicBehaviorEventType.PROGRESS, 30_000L, 25_000L));
        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), session, exposure, track.id(), MusicBehaviorEventType.PROGRESS, 45_000L, 40_000L));
        personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), session, exposure, track.id(), MusicBehaviorEventType.COMPLETE, 110_000L, 100_000L));

        assertThat(personalizationService.recordEvent(userA, new MusicBehaviorEventRequest(
                UUID.randomUUID(), session, exposure, track.id(), MusicBehaviorEventType.PLAY_START, 0L, 0L))
                .duplicate()).isTrue();
        var analytics = personalizationService.profile(userA).analytics();
        assertThat(analytics.playCount()).isEqualTo(1);
        assertThat(analytics.completeCount()).isEqualTo(1);
        assertThat(analytics.totalPlaybackMs()).isEqualTo(100_000);
        assertThat(analytics.topTracks()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Session Track");
            assertThat(item.playCount()).isEqualTo(1);
        });
    }

    private UUID expose(long userId, UUID conversationId, MusicTrackBo track) {
        UUID exposure = UUID.randomUUID();
        var item = new MusicPersonalizationRepository.ExposureTrack(track, Map.of("catalog", 1),
                Map.of("semantic", 0.7, "structured", 0.8, "rrf", 1.0, "personal", 0.0,
                        "freshness", 1.0, "longtail", 1.0, "exposurePenalty", 0.0,
                        "personalDelta", 0.06, "tags", List.of("focused")),
                List.of("CATALOG_MATCH"), List.of("focused"), 0.8, false);
        repository.recordExposure(userId, conversationId, exposure, "测试推荐", Map.of("intent", "DISCOVERY"),
                "baseline-v1", MusicPersonalizationStatus.COLD_START, List.of(item));
        return exposure;
    }

    private void insertConversation(UUID id, long userId) {
        jdbc.update("""
                INSERT INTO agent_conversation(id, user_id, title, created_at, updated_at, is_deleted)
                VALUES (?, ?, '推荐测试', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0)
                """, id.toString(), userId);
    }

    private void clearPersonalizationData() {
        jdbc.update("DELETE FROM music_playlist_track");
        jdbc.update("DELETE FROM music_playlist");
        jdbc.update("DELETE FROM music_graph_outbox");
        jdbc.update("DELETE FROM music_behavior_event");
        jdbc.update("DELETE FROM music_playback_session");
        jdbc.update("DELETE FROM music_user_track_stat");
        jdbc.update("DELETE FROM music_track_enrichment");
        jdbc.update("DELETE FROM music_track_tag");
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
        return new MusicTrackBo(id, name, List.of(artist), "Album", "https://image", 120_000,
                "https://external", "qq", "audio", "/api/music/qq/playback/1", null);
    }
}
