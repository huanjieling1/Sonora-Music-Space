package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicMatchType;
import com.example.agent.service.MusicCatalogProvider;
import com.example.agent.service.MusicQueryPlanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;

class MusicRecommendationServiceImplTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void returnsDirectAudioWithoutCallingYoutubeWhenLimitIsMet() {
        MusicQueryPlanner planner = planner();
        MusicCatalogProvider jamendo = provider("jamendo", false, 10);
        MusicCatalogProvider youtube = provider("youtube", true, 100);
        List<MusicTrackBo> tracks = List.of(track("jamendo:1", "One", "Artist", "jamendo", "audio"),
                track("jamendo:2", "Two", "Artist", "jamendo", "audio"));
        when(jamendo.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenReturn(tracks);

        var service = service(planner, List.of(jamendo, youtube), 5);
        var result = service.recommend(new MusicRecommendationAo("适合深夜写代码的电子乐", 2));

        assertThat(result.tracks()).extracting(MusicTrackBo::id)
                .containsExactly("jamendo:1", "jamendo:2");
        assertThat(result.providers()).containsExactly("jamendo");
        verify(youtube, never()).search(any(MusicSearchTask.class), anyInt(), anyInt());
    }

    @Test
    void deduplicatesDirectResultsAndUsesYoutubeOnlyToFillMissingTracks() {
        MusicQueryPlanner planner = planner();
        MusicCatalogProvider jamendo = provider("jamendo", false, 10);
        MusicCatalogProvider audius = provider("audius", false, 20);
        MusicCatalogProvider youtube = provider("youtube", true, 100);
        MusicTrackBo first = track("jamendo:1", "Night Drive", "The Artist", "jamendo", "audio");
        MusicTrackBo duplicate = track("audius:1", "Night-Drive", "the artist", "audius", "audio");
        MusicTrackBo second = track("audius:2", "Quiet Code", "Another", "audius", "audio");
        MusicTrackBo fallback = track("youtube:3", "Mainstream Song", "Channel", "youtube", "youtube");
        when(jamendo.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenReturn(List.of(first));
        when(audius.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenReturn(List.of(duplicate, second));
        when(youtube.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenReturn(List.of(fallback));

        var result = service(planner, List.of(youtube, audius, jamendo), 5)
                .recommend(new MusicRecommendationAo("适合深夜写代码的电子乐", 3));

        assertThat(result.tracks()).extracting(MusicTrackBo::id)
                .containsExactly(first.id(), second.id(), fallback.id());
        assertThat(result.providers()).containsExactly("jamendo", "audius", "youtube");
        verify(youtube).search(any(MusicSearchTask.class), anyInt(), anyInt());
    }

    @Test
    void keepsPartialResultsWhenOneDirectProviderFails() {
        MusicQueryPlanner planner = planner();
        MusicCatalogProvider jamendo = provider("jamendo", false, 10);
        MusicCatalogProvider audius = provider("audius", false, 20);
        MusicTrackBo track = track("audius:1", "Available", "Artist", "audius", "audio");
        when(jamendo.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenThrow(new IllegalStateException("down"));
        when(audius.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenReturn(List.of(track));

        var result = service(planner, List.of(jamendo, audius), 5)
                .recommend(new MusicRecommendationAo("适合深夜写代码的电子乐", 1));

        assertThat(result.tracks()).extracting(MusicTrackBo::id).containsExactly(track.id());
    }

    @Test
    void timesOutSlowProviderWithoutBlockingSuccessfulResults() {
        MusicQueryPlanner planner = planner();
        MusicCatalogProvider jamendo = provider("jamendo", false, 10);
        MusicCatalogProvider audius = provider("audius", false, 20);
        MusicTrackBo track = track("audius:1", "Fast Result", "Artist", "audius", "audio");
        when(jamendo.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenAnswer(invocation -> {
            Thread.sleep(2500);
            return List.of();
        });
        when(audius.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenReturn(List.of(track));
        long startedAt = System.nanoTime();

        var result = service(planner, List.of(jamendo, audius), 1)
                .recommend(new MusicRecommendationAo("适合深夜写代码的电子乐", 1));

        assertThat(result.tracks()).extracting(MusicTrackBo::id).containsExactly(track.id());
        assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(1800);
    }

    @Test
    void reportsFriendlyFailureWhenEveryConfiguredProviderFails() {
        MusicQueryPlanner planner = planner();
        MusicCatalogProvider jamendo = provider("jamendo", false, 10);
        when(jamendo.search(any(MusicSearchTask.class), anyInt(), anyInt())).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service(planner, List.of(jamendo), 5)
                .recommend(new MusicRecommendationAo("适合深夜写代码的电子乐", 1)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("暂时无法访问");
    }

    @Test
    void marksCatalogReadyOnlyWhenDirectAudioProviderIsConfigured() {
        MusicCatalogProvider youtube = provider("youtube", true, 100);
        when(youtube.configured()).thenReturn(true);
        var youtubeOnly = service(planner(), List.of(youtube), 5).status();

        MusicCatalogProvider jamendo = provider("jamendo", false, 10);
        var direct = service(planner(), List.of(jamendo, youtube), 5).status();

        assertThat(youtubeOnly.ready()).isFalse();
        assertThat(direct.ready()).isTrue();
    }

    @Test
    void loadsRequestedPageWithTenTracksAndStopsAtTwentyPages() {
        MusicCatalogProvider jamendo = provider("jamendo", false, 10);
        List<MusicTrackBo> pageTracks = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> track("jamendo:" + index, "Track " + index,
                        "Artist " + index, "jamendo", "audio"))
                .toList();
        when(jamendo.search(any(MusicSearchTask.class), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(10))).thenReturn(pageTracks);

        var result = service(planner(), List.of(jamendo), 5)
                .recommend(new MusicRecommendationAo("适合深夜写代码的电子乐", 2, 10));

        assertThat(result.tracks()).hasSize(10);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.maxPages()).isEqualTo(20);
        verify(jamendo).search(any(MusicSearchTask.class), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(10));
    }

    @Test
    void sendsCleanChineseEntityDirectlyToQqWithPlannerUnderstanding() {
        MusicQueryPlanner planner = mock(MusicQueryPlanner.class);
        MusicSearchTask noisy = new MusicSearchTask(MusicSearchTaskType.ENTITY,
                "进击的巨人 official music", null, null, null);
        when(planner.plan("我想要找到进击的巨人的歌曲")).thenReturn(new MusicSearchPlan(
                MusicSearchIntent.ENTITY_RELATED, null, List.of(), null, List.of(),
                List.of(), List.of(), List.of(noisy), 0.8, null));
        MusicCatalogProvider qq = provider("qq", false, 1);
        when(qq.search(org.mockito.ArgumentMatchers.<MusicSearchTask>argThat(task ->
                        "进击的巨人".equals(task.query())), anyInt(), anyInt()))
                .thenReturn(List.of(track("qq:1", "Call of Silence", "澤野弘之",
                                "TV动画《进击的巨人》原声", "qq", "audio"),
                        track("qq:2", "悪魔の子", "ヒグチアイ",
                                "进击的巨人 The Final Season", "qq", "audio")));
        var service = new MusicRecommendationServiceImpl(planner, new MusicCandidateRanker(),
                List.of(qq), executor, properties(5));

        var result = service.recommend(new MusicRecommendationAo("我想要找到进击的巨人的歌曲", 1, 2));

        assertThat(result.understanding().resolved()).isTrue();
        assertThat(result.understanding().canonicalName()).isEqualTo("进击的巨人");
        assertThat(result.understanding().knowledgeSources()).containsExactly("music_planner");
        assertThat(result.searchQuery()).isEqualTo("进击的巨人");
        assertThat(result.verifiedCount()).isEqualTo(2);
        assertThat(result.relatedCount()).isZero();
        assertThat(result.tracks()).extracting(MusicTrackBo::id).containsExactly("qq:1", "qq:2");
        assertThat(result.tracks()).extracting(MusicTrackBo::matchType)
                .containsOnly(MusicMatchType.VERIFIED);
        verify(qq).search(org.mockito.ArgumentMatchers.<MusicSearchTask>argThat(task ->
                "进击的巨人".equals(task.query())), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(2));
    }

    @Test
    void appliesStoredUserCorrectionBeforeSearchingProviders() {
        MusicQueryPlanner planner = mock(MusicQueryPlanner.class);
        when(planner.plan("我说的是那个 Faded")).thenReturn(new MusicSearchPlan(
                MusicSearchIntent.AMBIGUOUS, null, List.of(), null, List.of(), List.of(), List.of(),
                List.of(new MusicSearchTask(MusicSearchTaskType.KEYWORDS,
                        "那个 Faded", null, null, null)), 0.4, "请确认类型"));
        MusicKnowledgeRepository repository = mock(MusicKnowledgeRepository.class);
        long userId = 42L;
        UUID conversationId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        when(repository.findCorrection(userId, MusicTextNormalizer.normalize("我说的是那个 Faded")))
                .thenReturn(Optional.of(new MusicKnowledgeRepository.EntityRow(
                        88L, "Faded", com.example.agent.model.bo.MusicEntityType.TRACK,
                        List.of(), 1, "user")));
        when(repository.aliasesFor(88L)).thenReturn(List.of("Faded"));
        when(repository.rejectedTracks(userId, "Faded")).thenReturn(List.of());
        MusicCatalogProvider qq = provider("qq", false, 1);
        when(qq.search(org.mockito.ArgumentMatchers.<MusicSearchTask>argThat(task ->
                        "Faded".equals(task.query())), anyInt(), anyInt()))
                .thenReturn(List.of(track("qq:1", "Faded", "Alan Walker", "qq", "audio")));

        var service = new MusicRecommendationServiceImpl(planner, new MusicSearchPlanCompiler(),
                new MusicCandidateVerifier(), repository, new MusicCandidateRanker(),
                List.of(qq), executor, properties(5));
        var result = service.recommend(new MusicRecommendationAo(
                userId, conversationId, "我说的是那个 Faded", 1, 10));

        assertThat(result.tracks()).extracting(MusicTrackBo::id).containsExactly("qq:1");
        assertThat(result.understanding().canonicalName()).isEqualTo("Faded");
        assertThat(result.understanding().knowledgeSources()).containsExactly("user_correction");
        verify(qq).search(org.mockito.ArgumentMatchers.<MusicSearchTask>argThat(task ->
                "Faded".equals(task.query())), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(10));
    }

    private MusicRecommendationServiceImpl service(MusicQueryPlanner planner,
                                                   List<MusicCatalogProvider> providers,
                                                   int timeoutSeconds) {
        return new MusicRecommendationServiceImpl(planner, new MusicCandidateRanker(),
                providers, executor, properties(timeoutSeconds));
    }

    private static MusicQueryPlanner planner() {
        MusicQueryPlanner planner = mock(MusicQueryPlanner.class);
        MusicSearchTask task = new MusicSearchTask(MusicSearchTaskType.SCENE,
                "deep focus electronic night", null, null, null);
        MusicSearchPlan plan = new MusicSearchPlan(MusicSearchIntent.DISCOVERY, null, List.of(), null,
                List.of("electronic"), List.of("focused"), List.of("coding", "late night"),
                List.of(task), 0.95, null);
        when(planner.plan("适合深夜写代码的电子乐")).thenReturn(plan);
        return planner;
    }

    private static MusicCatalogProvider provider(String id, boolean fallbackOnly, int order) {
        MusicCatalogProvider provider = mock(MusicCatalogProvider.class);
        when(provider.id()).thenReturn(id);
        when(provider.displayName()).thenReturn(id);
        when(provider.configured()).thenReturn(true);
        when(provider.fallbackOnly()).thenReturn(fallbackOnly);
        when(provider.order()).thenReturn(order);
        when(provider.playbackTypes()).thenReturn(List.of(fallbackOnly ? "youtube" : "audio"));
        return provider;
    }

    private static MusicTrackBo track(String id, String name, String artist, String provider, String playbackType) {
        return track(id, name, artist, "Album", provider, playbackType);
    }

    private static MusicTrackBo track(String id, String name, String artist, String album,
                                      String provider, String playbackType) {
        return new MusicTrackBo(id, name, List.of(artist), album, "https://image", 180000,
                "https://external", provider, playbackType,
                playbackType.equals("youtube") ? id.substring(id.indexOf(':') + 1) : "https://audio", null);
    }

    private static MusicCatalogProperties properties(int timeoutSeconds) {
        return new MusicCatalogProperties(timeoutSeconds,
                new MusicCatalogProperties.Jamendo("jamendo-key", "https://jamendo.test"),
                new MusicCatalogProperties.Audius("audius-key", "https://audius.test"),
                new MusicCatalogProperties.Youtube("youtube-key", "https://youtube.test"));
    }
}
