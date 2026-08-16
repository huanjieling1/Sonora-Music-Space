package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicCatalogSearchBo;
import com.example.agent.model.bo.MusicCatalogSearchType;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicPlaylistBo;
import com.example.agent.model.bo.MusicSearchArtistBo;
import com.example.agent.model.bo.MusicSearchGenreBo;
import com.example.agent.model.bo.MusicSearchPlaylistBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.MusicCatalogProvider;
import com.example.agent.service.MusicCatalogSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.example.agent.service.impl.MusicPersonalizationRepository.ExposureTrack;

@Service
public class MusicCatalogSearchServiceImpl implements MusicCatalogSearchService {
    private static final Logger log = LoggerFactory.getLogger(MusicCatalogSearchServiceImpl.class);
    private static final String POLICY_VERSION = "catalog-search-v1";
    private static final List<GenreDefinition> GENRES = List.of(
            new GenreDefinition("pop", "流行", "旋律鲜明、覆盖华语与国际流行", List.of("流行", "pop", "华语流行")),
            new GenreDefinition("rock", "摇滚", "吉他、鼓组与更强烈的现场能量", List.of("摇滚", "rock", "流行摇滚", "独立摇滚")),
            new GenreDefinition("electronic", "电子", "合成器、电子节拍与未来感音色", List.of("电子", "electronic", "edm", "电音")),
            new GenreDefinition("jazz", "爵士", "即兴、摇摆节奏与丰富和声", List.of("爵士", "jazz", "swing")),
            new GenreDefinition("classical", "古典", "管弦乐、室内乐与钢琴作品", List.of("古典", "classical", "交响", "钢琴")),
            new GenreDefinition("folk", "民谣", "木吉他、叙事人声与自然质感", List.of("民谣", "folk", "民歌")),
            new GenreDefinition("hiphop", "嘻哈", "说唱、律动与采样文化", List.of("嘻哈", "hip-hop", "hiphop", "说唱", "rap")),
            new GenreDefinition("rnb", "R&B", "细腻人声、灵魂乐与节奏布鲁斯", List.of("r&b", "rnb", "节奏布鲁斯", "灵魂乐", "soul")),
            new GenreDefinition("metal", "金属", "高增益吉他、强劲鼓点与厚重音墙", List.of("金属", "metal", "重金属")),
            new GenreDefinition("ambient", "氛围", "空间感、环境音与低干扰聆听", List.of("氛围", "ambient", "白噪音", "冥想")),
            new GenreDefinition("acg", "ACG", "动漫、游戏与二次元相关音乐", List.of("acg", "动漫", "二次元", "游戏音乐", "anime")),
            new GenreDefinition("world", "世界音乐", "来自不同地域和传统的声音", List.of("世界音乐", "world", "民族", "拉丁", "雷鬼", "reggae"))
    );

    private final List<MusicCatalogProvider> providers;
    private final ExecutorService executor;
    private final int timeoutSeconds;
    private final MusicPersonalizationRepository personalization;
    private final MusicPlaylistRepository playlists;

    public MusicCatalogSearchServiceImpl(List<MusicCatalogProvider> providers,
                                         @Qualifier("musicProviderExecutor") ExecutorService executor,
                                         MusicCatalogProperties properties,
                                         MusicPersonalizationRepository personalization,
                                         MusicPlaylistRepository playlists) {
        this.providers = providers.stream().sorted(Comparator.comparingInt(MusicCatalogProvider::order)).toList();
        this.executor = executor;
        this.timeoutSeconds = properties.resolvedTimeoutSeconds();
        this.personalization = personalization;
        this.playlists = playlists;
    }

    @Override
    public MusicCatalogSearchBo search(long userId, UUID conversationId, String keyword,
                                       MusicCatalogSearchType type, int page, int pageSize) {
        String query = keyword == null ? "" : keyword.strip();
        if (query.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请输入歌曲、歌手、曲风或歌单名称");
        }
        List<MusicCatalogProvider> available = providers.stream().filter(MusicCatalogProvider::configured).toList();
        if (available.isEmpty()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "音乐曲库尚未配置");
        }

        int candidateLimit = Math.min(30, Math.max(pageSize, type == MusicCatalogSearchType.ALL ? 16 : 24));
        List<ProviderResult> results = searchProviders(available, query, page, candidateLimit);
        LinkedHashMap<String, MusicTrackBo> unique = new LinkedHashMap<>();
        results.forEach(result -> result.tracks().forEach(track -> unique.putIfAbsent(MusicTrackIdentity.key(track), track)));
        List<MusicTrackBo> candidates = List.copyOf(unique.values());
        List<MusicTrackBo> visibleTracks = candidates.stream().limit(pageSize).toList();
        int artistLimit = type == MusicCatalogSearchType.ARTIST ? pageSize : 8;
        List<MusicSearchArtistBo> artists = artists(candidates, artistLimit);
        List<MusicSearchGenreBo> genres = genres(query, type == MusicCatalogSearchType.GENRE ? pageSize : 8);
        List<MusicSearchPlaylistBo> foundPlaylists = playlists(userId, query, results,
                type == MusicCatalogSearchType.PLAYLIST ? pageSize : 8);

        UUID exposureId = UUID.randomUUID();
        List<MusicTrackBo> exposureTracks = switch (type) {
            case TRACK -> visibleTracks;
            case ALL, PLAYLIST -> candidates;
            case ARTIST, GENRE -> List.of();
        };
        if (!exposureTracks.isEmpty()) {
            personalization.recordExposure(userId, conversationId, exposureId, "曲库搜索：" + query,
                    Map.of("intent", "CATALOG_SEARCH", "type", type.name(), "keyword", query),
                    POLICY_VERSION, MusicPersonalizationStatus.DISABLED, exposure(exposureTracks, type));
        }
        boolean hasNext = candidates.size() >= candidateLimit && page < 20;
        List<String> usedProviders = results.stream().filter(result -> !result.tracks().isEmpty())
                .map(ProviderResult::provider).toList();
        return new MusicCatalogSearchBo(exposureId, query, type, visibleTracks, artists, genres,
                foundPlaylists, page, pageSize, hasNext, usedProviders);
    }

    private List<ProviderResult> searchProviders(List<MusicCatalogProvider> available, String query,
                                                 int page, int limit) {
        List<CompletableFuture<ProviderResult>> tasks = available.stream().map(provider ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return new ProviderResult(provider.id(), provider.displayName(),
                                provider.search(query, page, limit));
                    } catch (RuntimeException exception) {
                        log.warn("Catalog search provider {} failed: {}", provider.id(),
                                exception.getClass().getSimpleName());
                        return new ProviderResult(provider.id(), provider.displayName(), List.of());
                    }
                }, executor).completeOnTimeout(new ProviderResult(provider.id(), provider.displayName(), List.of()),
                        timeoutSeconds, TimeUnit.SECONDS)).toList();
        return tasks.stream().map(CompletableFuture::join).toList();
    }

    private static List<MusicSearchArtistBo> artists(List<MusicTrackBo> tracks, int limit) {
        LinkedHashMap<String, ArtistAccumulator> found = new LinkedHashMap<>();
        for (MusicTrackBo track : tracks) {
            for (String artist : track.artists()) {
                String normalized = MusicTrackIdentity.normalize(artist);
                if (normalized.isEmpty()) continue;
                ArtistAccumulator value = found.computeIfAbsent(normalized,
                        ignored -> new ArtistAccumulator(artist, track.imageUrl(), track.provider()));
                value.matchedTracks++;
                if (value.imageUrl == null && track.imageUrl() != null) value.imageUrl = track.imageUrl();
            }
        }
        return found.entrySet().stream().limit(limit)
                .map(entry -> new MusicSearchArtistBo("artist:" + MusicTrackIdentity.sha256(entry.getKey()).substring(0, 16),
                        entry.getValue().name, entry.getValue().imageUrl, entry.getValue().provider,
                        entry.getValue().matchedTracks))
                .toList();
    }

    private static List<MusicSearchGenreBo> genres(String query, int limit) {
        String normalized = MusicTrackIdentity.normalize(query);
        return GENRES.stream()
                .filter(genre -> genre.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || genre.aliases().stream().map(MusicTrackIdentity::normalize)
                        .anyMatch(alias -> alias.contains(normalized) || normalized.contains(alias)))
                .limit(limit)
                .map(genre -> new MusicSearchGenreBo(genre.id(), genre.name(), genre.description(), genre.name()))
                .toList();
    }

    private List<MusicSearchPlaylistBo> playlists(long userId, String query, List<ProviderResult> results, int limit) {
        List<MusicSearchPlaylistBo> found = new ArrayList<>();
        String normalized = MusicTrackIdentity.normalize(query);
        for (MusicPlaylistBo playlist : playlists.list(userId)) {
            String searchable = MusicTrackIdentity.normalize(playlist.name() + " "
                    + (playlist.description() == null ? "" : playlist.description()));
            if (searchable.contains(normalized)) {
                found.add(new MusicSearchPlaylistBo(playlist.id().toString(), playlist.name(),
                        playlist.description(), playlist.coverUrl(), "local", playlist.trackCount(), true, List.of()));
                if (found.size() >= limit) return List.copyOf(found);
            }
        }
        for (ProviderResult result : results) {
            if (result.tracks().isEmpty() || found.size() >= limit) continue;
            List<MusicTrackBo> tracks = result.tracks().stream().limit(20).toList();
            String cover = tracks.stream().map(MusicTrackBo::imageUrl).filter(value -> value != null && !value.isBlank())
                    .findFirst().orElse(null);
            found.add(new MusicSearchPlaylistBo("online:" + result.provider(),
                    query + " · " + result.displayName() + "搜索合集",
                    "来自" + result.displayName() + "的实时搜索结果，不会自动保存到我的歌单",
                    cover, result.provider(), tracks.size(), false, tracks));
        }
        return List.copyOf(found);
    }

    private static List<ExposureTrack> exposure(List<MusicTrackBo> tracks, MusicCatalogSearchType type) {
        List<ExposureTrack> result = new ArrayList<>();
        int rank = 0;
        for (MusicTrackBo track : tracks) {
            rank++;
            result.add(new ExposureTrack(track, Map.of("catalogSearch", rank),
                    Map.of("searchType", type.name(), "catalogRank", rank),
                    List.of("CATALOG_SEARCH"), List.of("search"), 1.0 / rank, false));
        }
        return List.copyOf(result);
    }

    private record ProviderResult(String provider, String displayName, List<MusicTrackBo> tracks) {
        private ProviderResult {
            tracks = tracks == null ? List.of() : List.copyOf(tracks);
        }
    }

    private record GenreDefinition(String id, String name, String description, List<String> aliases) {
    }

    private static final class ArtistAccumulator {
        private final String name;
        private String imageUrl;
        private final String provider;
        private int matchedTracks;

        private ArtistAccumulator(String name, String imageUrl, String provider) {
            this.name = name;
            this.imageUrl = imageUrl;
            this.provider = provider;
        }
    }
}
