package com.example.agent.service.impl;

import com.example.agent.config.MusicKnowledgeProperties;
import com.example.agent.model.bo.MusicEntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicKnowledgeResolverTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void resolvesChineseEnglishAndShortAliasesToValorant() {
        MusicKnowledgeRepository repository = repositoryWithValorantAliases();
        MusicKnowledgeResolver resolver = resolver(repository, List.of(), properties(1));

        assertThat(resolver.resolve("我是说无畏契约的").canonicalName()).isEqualTo("VALORANT");
        assertThat(resolver.resolve("VALORANT 的音乐").canonicalName()).isEqualTo("VALORANT");
        assertThat(resolver.resolve("瓦").canonicalName()).isEqualTo("VALORANT");
        assertThat(resolver.resolve("瓦房店的音乐").resolved()).isFalse();
    }

    @Test
    void usesCachedPublicKnowledgeWithoutCallingProviderAgain() throws Exception {
        MusicKnowledgeRepository repository = mock(MusicKnowledgeRepository.class);
        when(repository.findCorrection(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(repository.aliases()).thenReturn(List.of());
        when(repository.rejectedTracks("原神")).thenReturn(List.of());
        PublicMusicKnowledgeProvider provider = mock(PublicMusicKnowledgeProvider.class);
        when(provider.id()).thenReturn("wikidata");
        when(provider.enabled()).thenReturn(true);
        ExternalMusicEntity cached = new ExternalMusicEntity("原神", MusicEntityType.GAME,
                List.of("原神", "Genshin Impact"), 0.92, "wikidata", "Q123");
        when(repository.cache("原神", "wikidata")).thenReturn(Optional.of(
                new MusicKnowledgeRepository.CacheRow(true, objectMapper.writeValueAsString(cached),
                        LocalDateTime.now().plusDays(1))));

        MusicKnowledgeResolver resolver = resolver(repository, List.of(provider), properties(1));
        var result = resolver.resolve("原神的音乐");

        assertThat(result.canonicalName()).isEqualTo("原神");
        assertThat(result.knowledgeSources()).containsExactly("wikidata");
        verify(provider, never()).lookup("原神");
    }

    @Test
    void timesOutPublicKnowledgeWithoutBreakingSearchPlanning() {
        MusicKnowledgeRepository repository = mock(MusicKnowledgeRepository.class);
        when(repository.findCorrection("未知游戏作品")).thenReturn(Optional.empty());
        when(repository.aliases()).thenReturn(List.of());
        when(repository.cache("未知游戏作品", "slow")).thenReturn(Optional.empty());
        PublicMusicKnowledgeProvider slow = new PublicMusicKnowledgeProvider() {
            public String id() { return "slow"; }
            public boolean enabled() { return true; }
            public Optional<ExternalMusicEntity> lookup(String candidate) {
                try {
                    Thread.sleep(1800);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
        };
        MusicKnowledgeResolver resolver = resolver(repository, List.of(slow), properties(1));
        long started = System.nanoTime();

        var result = resolver.resolve("未知游戏作品");

        assertThat(result.resolved()).isFalse();
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1500);
    }

    private MusicKnowledgeRepository repositoryWithValorantAliases() {
        MusicKnowledgeRepository repository = mock(MusicKnowledgeRepository.class);
        when(repository.findCorrection(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        var aliases = List.of(
                new MusicKnowledgeRepository.AliasRow(1, "无畏契约", "无畏契约", 10,
                        "VALORANT", MusicEntityType.GAME, List.of("cinematic"), 1, "curated"),
                new MusicKnowledgeRepository.AliasRow(1, "VALORANT", "valorant", 10,
                        "VALORANT", MusicEntityType.GAME, List.of("cinematic"), 1, "curated"),
                new MusicKnowledgeRepository.AliasRow(1, "瓦", "瓦", 80,
                        "VALORANT", MusicEntityType.GAME, List.of("cinematic"), 1, "curated"));
        when(repository.aliases()).thenReturn(aliases);
        when(repository.aliasesFor(1)).thenReturn(List.of("VALORANT", "无畏契约", "瓦"));
        when(repository.relationsFor(1)).thenReturn(List.of());
        when(repository.rejectedTracks("VALORANT")).thenReturn(List.of());
        return repository;
    }

    private MusicKnowledgeResolver resolver(MusicKnowledgeRepository repository,
                                            List<PublicMusicKnowledgeProvider> providers,
                                            MusicKnowledgeProperties properties) {
        return new MusicKnowledgeResolver(repository, providers, properties, executor, objectMapper);
    }

    private static MusicKnowledgeProperties properties(int timeoutSeconds) {
        return new MusicKnowledgeProperties(true, timeoutSeconds, 30, 1,
                new MusicKnowledgeProperties.Wikidata(true, "https://wikidata.test"),
                new MusicKnowledgeProperties.MusicBrainz(true, "https://musicbrainz.test", "Sonora/Test"));
    }
}
