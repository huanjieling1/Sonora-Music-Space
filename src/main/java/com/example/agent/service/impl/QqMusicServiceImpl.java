package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.QqMusicStatusBo;
import com.example.agent.model.bo.QqMusicPlaybackBo;
import com.example.agent.model.bo.MusicLyricLineBo;
import com.example.agent.model.bo.MusicLyricsBo;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.QqMusicService;
import com.example.agent.model.bo.QqPublicPlaylistBo;
import com.example.agent.model.bo.QqMusicSearchBo;
import com.example.agent.model.bo.QqMusicSearchType;
import com.example.agent.model.bo.QqArtistDetailBo;
import com.example.agent.model.bo.QqAlbumDetailBo;
import com.example.agent.model.bo.QqVideoPlaybackBo;
import com.example.agent.model.bo.QqMusicQrLoginBo;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class QqMusicServiceImpl implements QqMusicService {
    private static final List<String> QUALITY_ORDER = List.of("flac", "320", "128", "m4a");
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern META_TAG = Pattern.compile("^\\[[a-zA-Z]+:.*]$");

    private final MusicCatalogProperties.Qq configuration;
    private final QqMusicSidecarClient sidecar;
    private final QqMusicSessionStore sessionStore;
    private final MusicPersonalizationRepository personalization;
    private final ConcurrentMap<String, Long> qrLoginOwners = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> activeQrLogins = new ConcurrentHashMap<>();

    @Autowired
    public QqMusicServiceImpl(MusicCatalogProperties properties,
                              QqMusicSidecarClient sidecar,
                              QqMusicSessionStore sessionStore,
                              MusicPersonalizationRepository personalization) {
        this.configuration = properties.qq();
        this.sidecar = sidecar;
        this.sessionStore = sessionStore;
        this.personalization = personalization;
    }

    QqMusicServiceImpl(MusicCatalogProperties properties,
                       QqMusicSidecarClient sidecar,
                       QqMusicSessionStore sessionStore) {
        this(properties, sidecar, sessionStore, null);
    }

    @Override
    public QqMusicStatusBo status() {
        boolean enabled = configuration != null && configuration.configured();
        boolean available = enabled && sidecar.healthy();
        boolean session = sessionStore.hasSession();
        String message;
        if (!enabled) {
            message = "QQ 音乐本机接入已关闭";
        } else if (!available) {
            message = "QQ 音乐本机 Bridge 未启动";
        } else if (!session) {
            message = "请使用手机 QQ 或 QQ 音乐扫码连接";
        } else {
            message = "QQ 音乐已接入；实际播放范围取决于当前账号权益";
        }
        return new QqMusicStatusBo(enabled, available, session, sessionStore.maskedAccount(), message);
    }

    @Override
    public QqMusicStatusBo saveSession(String cookie) {
        if (configuration == null || !configuration.configured()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐本机接入未启用");
        }
        try {
            sessionStore.save(cookie);
        } catch (IllegalArgumentException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "无法安全保存 QQ 音乐登录态");
        }
        return status();
    }

    @Override
    public QqMusicStatusBo clearSession() {
        try {
            sessionStore.clear();
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "无法清除 QQ 音乐登录态");
        }
        return status();
    }

    @Override
    public QqMusicQrLoginBo startQrLogin(long userId) {
        requireBridge();
        String previous = activeQrLogins.remove(userId);
        if (previous != null && Long.valueOf(userId).equals(qrLoginOwners.remove(previous))) {
            try {
                sidecar.cancelQrLogin(previous);
            } catch (RuntimeException ignored) {
                // The previous attempt may already have expired in the local bridge.
            }
        }
        try {
            JsonNode response = sidecar.startQrLogin();
            String loginId = response == null ? "" : response.path("loginId").asText("");
            String qrImage = response == null ? "" : response.path("qrImage").asText("");
            String loginMode = response == null ? "" : response.path("loginMode").asText("QR");
            boolean validLoginView = "BROWSER".equals(loginMode)
                    || qrImage.startsWith("data:image/png;base64,");
            if (!loginId.matches("[0-9a-fA-F-]{36}") || !validLoginView) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "无法打开 QQ 音乐登录窗口");
            }
            qrLoginOwners.put(loginId, userId);
            activeQrLogins.put(userId, loginId);
            return qrLogin(response, null);
        } catch (RestClientResponseException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "无法打开 QQ 音乐登录窗口");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "无法打开 QQ 音乐登录窗口");
        }
    }

    @Override
    public QqMusicQrLoginBo pollQrLogin(long userId, String loginId) {
        requireOwnedQrLogin(userId, loginId);
        try {
            JsonNode response = sidecar.pollQrLogin(loginId);
            if (response == null) throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐登录状态暂时不可用");
            String state = response.path("status").asText("ERROR");
            QqMusicStatusBo connection = null;
            if ("SUCCESS".equals(state)) {
                String cookie = response.path("cookie").asText("");
                if (!StringUtils.hasText(cookie)) {
                    throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐登录未返回有效会话");
                }
                sessionStore.save(cookie);
                connection = status();
                try {
                    sidecar.cancelQrLogin(loginId);
                } catch (RuntimeException ignored) {
                    // The credential is already encrypted locally. Expiry cleanup remains as a fallback.
                }
                releaseQrLogin(userId, loginId);
            } else if ("EXPIRED".equals(state) || "FAILED".equals(state)) {
                releaseQrLogin(userId, loginId);
            }
            return qrLogin(response, connection);
        } catch (RestClientResponseException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐登录状态暂时不可用");
        } catch (IllegalArgumentException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐登录态无效，请重新登录");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐登录状态暂时不可用");
        }
    }

    @Override
    public void cancelQrLogin(long userId, String loginId) {
        requireOwnedQrLogin(userId, loginId);
        try {
            sidecar.cancelQrLogin(loginId);
        } catch (RuntimeException ignored) {
            // Cancellation is idempotent; always forget the local ownership binding.
        } finally {
            releaseQrLogin(userId, loginId);
        }
    }

    private QqMusicQrLoginBo qrLogin(JsonNode response, QqMusicStatusBo connection) {
        return new QqMusicQrLoginBo(response.path("loginId").asText(null),
                response.path("loginMode").asText("QR"),
                response.path("status").asText("ERROR"), response.path("message").asText(""),
                response.path("qrImage").asText(null), response.path("expiresAt").asText(null), connection);
    }

    private void requireOwnedQrLogin(long userId, String loginId) {
        if (loginId == null || !loginId.matches("[0-9a-fA-F-]{36}")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "二维码登录标识不正确");
        }
        if (!Long.valueOf(userId).equals(qrLoginOwners.get(loginId))) {
            throw new AppException(HttpStatus.NOT_FOUND, "二维码登录已结束或不属于当前用户");
        }
    }

    private void releaseQrLogin(long userId, String loginId) {
        qrLoginOwners.remove(loginId, userId);
        activeQrLogins.remove(userId, loginId);
    }

    @Override
    public QqMusicPlaybackBo resolvePlayback(String songMid, String mediaId) {
        if (songMid == null || !songMid.matches("[A-Za-z0-9]+")
                || (StringUtils.hasText(mediaId) && !mediaId.matches("[A-Za-z0-9]+"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐曲目标识不正确");
        }
        String cookie = sessionStore.cookie().orElseThrow(() ->
                new AppException(HttpStatus.UNAUTHORIZED, "QQ 音乐登录态未配置，请先扫码连接"));
        if (!sidecar.healthy()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐本机 Bridge 未启动");
        }

        RuntimeException lastFailure = null;
        for (String quality : resolvedQualities()) {
            try {
                JsonNode response = sidecar.play(songMid, mediaId, quality, cookie);
                String url = response == null ? "" : response.path("url").asText("");
                if (StringUtils.hasText(url)) {
                    URI uri = URI.create(url);
                    if ("https".equalsIgnoreCase(uri.getScheme())) {
                        String resolvedQuality = response.path("quality").asText(quality);
                        return new QqMusicPlaybackBo(uri, resolvedQuality);
                    }
                }
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                if (exception.getStatusCode().value() == 401) {
                    throw new AppException(HttpStatus.UNAUTHORIZED, "QQ 音乐登录态已失效，请重新扫码连接");
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure != null && !(lastFailure instanceof RestClientResponseException)) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐接口暂时不可用，请稍后重试");
        }
        throw new AppException(HttpStatus.NOT_FOUND, "当前账号无法播放这首歌曲，已尝试自动降低音质");
    }

    @Override
    public MusicLyricsBo lyrics(String songMid) {
        if (songMid == null || !songMid.matches("[A-Za-z0-9]+")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐曲目标识不正确");
        }
        if (!sidecar.healthy()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐本机 Bridge 未启动");
        }
        try {
            JsonNode response = sidecar.lyrics(songMid);
            String original = response == null ? "" : response.path("lyric").asText("");
            String translated = response == null ? "" : response.path("translation").asText("");
            String romanized = response == null ? "" : response.path("romanization").asText("");
            List<MusicLyricLineBo> lines = mergeLyrics(original, translated, romanized);
            if (lines.isEmpty()) {
                return MusicLyricsBo.unavailable("qq", "qq:" + songMid, "这首歌曲暂未提供歌词");
            }
            boolean synced = lines.stream().anyMatch(line -> line.timeMs() != null);
            return new MusicLyricsBo("qq", "qq:" + songMid, true, synced, lines,
                    "QQ 音乐", synced ? "歌词已按播放进度同步" : "该歌曲仅提供纯文本歌词");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return MusicLyricsBo.unavailable("qq", "qq:" + songMid, "这首歌曲暂未提供歌词");
            }
            throw new AppException(HttpStatus.BAD_GATEWAY, "歌词服务暂时不可用，请稍后重试");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "歌词服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public QqMusicSearchBo search(long userId, UUID conversationId, String keyword,
                                  QqMusicSearchType type, int page, int pageSize) {
        String query = keyword == null ? "" : keyword.strip();
        if (!StringUtils.hasText(query)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请输入 QQ 音乐搜索关键词");
        }
        if (conversationId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "会话标识不能为空");
        }
        QqMusicSearchType resolvedType = type == null ? QqMusicSearchType.TRACK : type;
        int resolvedPage = Math.min(50, Math.max(1, page));
        int resolvedSize = Math.min(30, Math.max(5, pageSize));
        requireBridge();
        if (personalization != null) personalization.requireOwnedConversation(userId, conversationId);
        try {
            JsonNode response = sidecar.search(query, resolvedType.name(), resolvedSize, resolvedPage);
            if (response == null) throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐搜索暂时不可用");

            List<MusicTrackBo> tracks = mapTracks(response.path("tracks"));
            List<QqMusicSearchBo.Lyric> lyrics = new ArrayList<>();
            if (response.path("lyrics").isArray()) {
                for (JsonNode item : response.path("lyrics")) {
                    MusicTrackBo track = catalogTrack(item);
                    if (track != null) lyrics.add(new QqMusicSearchBo.Lyric(track, item.path("snippet").asText("")));
                }
            }
            List<MusicTrackBo> exposed = resolvedType == QqMusicSearchType.LYRIC
                    ? lyrics.stream().map(QqMusicSearchBo.Lyric::track).toList() : tracks;
            UUID exposureId = exposed.isEmpty() ? null : UUID.randomUUID();
            if (exposureId != null && personalization != null) {
                List<MusicPersonalizationRepository.ExposureTrack> exposureTracks = new ArrayList<>();
                for (int index = 0; index < exposed.size(); index++) {
                    MusicTrackBo track = exposed.get(index);
                    exposureTracks.add(new MusicPersonalizationRepository.ExposureTrack(track,
                            Map.of("qqSearch", index + 1), Map.of("type", resolvedType.name()),
                            List.of("QQ_SEARCH"), List.of(), Math.max(0.01, 1.0 - index * 0.002), false));
                }
                personalization.recordExposure(userId, conversationId, exposureId, "QQ 音乐搜索：" + query,
                        Map.of("intent", "QQ_SEARCH", "type", resolvedType.name(), "keyword", query),
                        "qq-search-v1", MusicPersonalizationStatus.DISABLED, exposureTracks);
            }

            return new QqMusicSearchBo(exposureId, response.path("keyword").asText(query), resolvedType,
                    response.path("page").asInt(resolvedPage), response.path("pageSize").asInt(resolvedSize),
                    Math.max(0, response.path("total").asLong()), response.path("hasNext").asBoolean(false),
                    tracks, mapArtists(response.path("artists")), mapAlbums(response.path("albums")),
                    mapSearchPlaylists(response.path("playlists")), mapVideos(response.path("videos")),
                    lyrics, mapUsers(response.path("users")));
        } catch (RestClientResponseException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐搜索暂时不可用");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐搜索暂时不可用");
        }
    }

    private List<MusicTrackBo> mapTracks(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<MusicTrackBo> tracks = new ArrayList<>();
        node.forEach(item -> {
            MusicTrackBo track = catalogTrack(item);
            if (track != null) tracks.add(track);
        });
        return List.copyOf(tracks);
    }

    private MusicTrackBo catalogTrack(JsonNode item) {
        String songMid = item.path("songMid").asText("");
        String mediaMid = item.path("mediaMid").asText(songMid);
        String name = item.path("name").asText("").trim();
        if (!songMid.matches("[A-Za-z0-9]+") || !StringUtils.hasText(name)) return null;
        List<String> artists = new ArrayList<>();
        if (item.path("artists").isArray()) item.path("artists").forEach(artist -> {
            if (StringUtils.hasText(artist.asText())) artists.add(artist.asText());
        });
        String albumMid = item.path("albumMid").asText("");
        String imageUrl = albumMid.matches("[A-Za-z0-9]+")
                ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg?max_age=2592000"
                : null;
        String playbackUrl = UriComponentsBuilder.fromPath("/api/music/qq/play/")
                .pathSegment(songMid).queryParam("mediaId", mediaMid).build().encode().toUriString();
        return new MusicTrackBo("qq:" + songMid, name, artists, item.path("album").asText(""), imageUrl,
                Math.max(0, item.path("durationMs").asLong()),
                "https://y.qq.com/n/ryqq/songDetail/" + songMid, "qq", "audio", playbackUrl, null)
                .withAlbumId(albumMid);
    }

    private List<QqMusicSearchBo.Artist> mapArtists(JsonNode node) {
        List<QqMusicSearchBo.Artist> values = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> values.add(new QqMusicSearchBo.Artist(
                item.path("id").asText(), item.path("mid").asText(), item.path("name").asText(),
                item.path("imageUrl").asText(null), Math.max(0, item.path("songCount").asLong()),
                Math.max(0, item.path("albumCount").asLong()), Math.max(0, item.path("videoCount").asLong()),
                item.path("externalUrl").asText(null))));
        return List.copyOf(values);
    }

    private List<QqMusicSearchBo.Album> mapAlbums(JsonNode node) {
        List<QqMusicSearchBo.Album> values = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> values.add(new QqMusicSearchBo.Album(
                item.path("id").asText(), item.path("mid").asText(), item.path("name").asText(),
                item.path("coverUrl").asText(null), textList(item.path("artists")),
                item.path("publishDate").asText(""), Math.max(0, item.path("trackCount").asLong()),
                item.path("externalUrl").asText(null))));
        return List.copyOf(values);
    }

    private List<QqMusicSearchBo.Playlist> mapSearchPlaylists(JsonNode node) {
        List<QqMusicSearchBo.Playlist> values = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> values.add(new QqMusicSearchBo.Playlist(
                item.path("id").asText(), item.path("name").asText(), item.path("description").asText(""),
                item.path("coverUrl").asText(null), item.path("creatorName").asText("QQ 音乐用户"),
                Math.max(0, item.path("listenCount").asLong()), Math.max(0, item.path("trackCount").asLong()),
                item.path("externalUrl").asText(null))));
        return List.copyOf(values);
    }

    private List<QqMusicSearchBo.Video> mapVideos(JsonNode node) {
        List<QqMusicSearchBo.Video> values = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> values.add(new QqMusicSearchBo.Video(
                item.path("id").asText(), item.path("name").asText(), item.path("coverUrl").asText(null),
                textList(item.path("artists")), Math.max(0, item.path("durationMs").asLong()),
                Math.max(0, item.path("playCount").asLong()), item.path("publishDate").asText(""),
                item.path("externalUrl").asText(null))));
        return List.copyOf(values);
    }

    private List<QqMusicSearchBo.User> mapUsers(JsonNode node) {
        List<QqMusicSearchBo.User> values = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> values.add(new QqMusicSearchBo.User(
                item.path("id").asText(), item.path("name").asText(), item.path("avatarUrl").asText(null),
                Math.max(0, item.path("followerCount").asLong()), Math.max(0, item.path("playlistCount").asLong()),
                item.path("badge").asText(""), item.path("externalUrl").asText(null))));
        return List.copyOf(values);
    }

    private List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> {
            if (StringUtils.hasText(item.asText())) values.add(item.asText());
        });
        return List.copyOf(values);
    }

    @Override
    public List<QqPublicPlaylistBo> publicPlaylists(int page, int pageSize) {
        int resolvedPage = Math.min(20, Math.max(1, page));
        int resolvedSize = Math.min(24, Math.max(1, pageSize));
        requireBridge();
        try {
            JsonNode response = sidecar.publicPlaylists(resolvedPage, resolvedSize);
            if (response == null || !response.path("playlists").isArray()) return List.of();
            List<QqPublicPlaylistBo> result = new ArrayList<>();
            for (JsonNode item : response.path("playlists")) {
                QqPublicPlaylistBo playlist = publicPlaylistSummary(item);
                if (playlist != null) result.add(playlist);
            }
            return List.copyOf(result);
        } catch (RestClientResponseException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐公开歌单暂时不可用");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐公开歌单暂时不可用");
        }
    }

    @Override
    public QqPublicPlaylistBo publicPlaylist(long userId, UUID conversationId, String playlistId,
                                             int limit, boolean shuffle) {
        if (playlistId == null || !playlistId.matches("\\d{5,20}")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐歌单标识不正确");
        }
        if (conversationId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "会话标识不能为空");
        }
        int resolvedLimit = Math.min(100, Math.max(1, limit));
        requireBridge();
        if (personalization != null) personalization.requireOwnedConversation(userId, conversationId);
        try {
            JsonNode response = sidecar.playlist(playlistId, resolvedLimit);
            if (response == null) throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐歌单不存在或未公开");
            QqPublicPlaylistBo summary = publicPlaylistSummary(response);
            if (summary == null) throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐歌单不存在或未公开");

            List<MusicTrackBo> tracks = new ArrayList<>();
            if (response.path("tracks").isArray()) {
                for (JsonNode item : response.path("tracks")) {
                    MusicTrackBo track = publicPlaylistTrack(item, summary);
                    if (track != null) tracks.add(track);
                }
            }
            if (shuffle) Collections.shuffle(tracks);
            UUID exposureId = tracks.isEmpty() ? null : UUID.randomUUID();
            if (exposureId != null && personalization != null) {
                List<MusicPersonalizationRepository.ExposureTrack> exposureTracks = new ArrayList<>();
                for (int index = 0; index < tracks.size(); index++) {
                    MusicTrackBo track = tracks.get(index);
                    exposureTracks.add(new MusicPersonalizationRepository.ExposureTrack(track,
                            Map.of("qqPublicPlaylist", index + 1),
                            Map.of("playlistId", playlistId, "creator", summary.creatorName()),
                            List.of("QQ_PUBLIC_PLAYLIST"), summary.tags(),
                            Math.max(0.01, 1.0 - index * 0.001), false));
                }
                personalization.recordExposure(userId, conversationId, exposureId,
                        "打开 QQ 音乐公开歌单：" + summary.name(),
                        Map.of("intent", "QQ_PUBLIC_PLAYLIST", "playlistId", playlistId,
                                "shuffle", shuffle),
                        "qq-public-v1", MusicPersonalizationStatus.COLD_START, exposureTracks);
            }
            return new QqPublicPlaylistBo(summary.id(), summary.name(), summary.description(),
                    summary.coverUrl(), summary.creatorName(), summary.creatorAvatarUrl(),
                    summary.listenCount(), Math.max(summary.trackCount(), tracks.size()), summary.tags(),
                    summary.externalUrl(), exposureId, tracks, "qq-public-v1",
                    MusicPersonalizationStatus.COLD_START);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐歌单不存在或未公开");
            }
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐歌单暂时无法打开");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐歌单暂时无法打开");
        }
    }

    @Override
    public QqArtistDetailBo artist(long userId, UUID conversationId, String artistMid,
                                   int songPage, int songPageSize, int albumPage, int albumPageSize) {
        if (artistMid == null || !artistMid.matches("[A-Za-z0-9]{5,30}")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐歌手标识不正确");
        }
        if (conversationId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "会话标识不能为空");
        }
        int resolvedSongPage = Math.min(50, Math.max(1, songPage));
        int resolvedSongSize = Math.min(30, Math.max(5, songPageSize));
        int resolvedAlbumPage = Math.min(50, Math.max(1, albumPage));
        int resolvedAlbumSize = Math.min(24, Math.max(5, albumPageSize));
        requireBridge();
        if (personalization != null) personalization.requireOwnedConversation(userId, conversationId);
        try {
            JsonNode response = sidecar.artist(artistMid, resolvedSongPage, resolvedSongSize,
                    resolvedAlbumPage, resolvedAlbumSize);
            if (response == null || !StringUtils.hasText(response.path("name").asText())) {
                throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐歌手不存在");
            }
            List<MusicTrackBo> tracks = new ArrayList<>();
            if (response.path("tracks").isArray()) {
                for (JsonNode item : response.path("tracks")) {
                    MusicTrackBo track = artistTrack(item, response.path("name").asText());
                    if (track != null) tracks.add(track);
                }
            }
            List<QqArtistDetailBo.Album> albums = new ArrayList<>();
            if (response.path("albums").isArray()) {
                for (JsonNode item : response.path("albums")) {
                    String mid = item.path("mid").asText("");
                    String name = item.path("name").asText("").trim();
                    if (mid.matches("[A-Za-z0-9]+") && StringUtils.hasText(name)) {
                        albums.add(new QqArtistDetailBo.Album(mid, name, item.path("coverUrl").asText(null),
                                item.path("publishDate").asText(""), item.path("type").asText(""),
                                Math.max(0, item.path("trackCount").asInt()),
                                item.path("externalUrl").asText("https://y.qq.com/n/ryqq/albumDetail/" + mid)));
                    }
                }
            }
            UUID exposureId = tracks.isEmpty() ? null : UUID.randomUUID();
            if (exposureId != null && personalization != null) {
                List<MusicPersonalizationRepository.ExposureTrack> exposureTracks = new ArrayList<>();
                for (int index = 0; index < tracks.size(); index++) {
                    MusicTrackBo track = tracks.get(index);
                    exposureTracks.add(new MusicPersonalizationRepository.ExposureTrack(track,
                            Map.of("qqArtist", index + 1), Map.of("artistMid", artistMid),
                            List.of("QQ_ARTIST_PAGE"), List.of(),
                            Math.max(0.01, 1.0 - index * 0.002), false));
                }
                personalization.recordExposure(userId, conversationId, exposureId,
                        "打开 QQ 音乐歌手：" + response.path("name").asText(),
                        Map.of("intent", "QQ_ARTIST_PAGE", "artistMid", artistMid),
                        "qq-artist-v1", MusicPersonalizationStatus.DISABLED, exposureTracks);
            }
            return new QqArtistDetailBo(exposureId, artistMid, response.path("name").asText(),
                    response.path("imageUrl").asText(null), response.path("foreignName").asText(""),
                    response.path("birthday").asText(""), response.path("area").asText(""),
                    response.path("description").asText(""),
                    response.path("externalUrl").asText("https://y.qq.com/n/ryqq/singer/" + artistMid),
                    Math.max(0, response.path("songTotal").asInt()),
                    Math.max(0, response.path("albumTotal").asInt()),
                    response.path("songPage").asInt(resolvedSongPage),
                    response.path("songPageSize").asInt(resolvedSongSize),
                    response.path("hasMoreSongs").asBoolean(false),
                    response.path("albumPage").asInt(resolvedAlbumPage),
                    response.path("albumPageSize").asInt(resolvedAlbumSize),
                    response.path("hasMoreAlbums").asBoolean(false), tracks, albums,
                    "qq-artist-v1", MusicPersonalizationStatus.DISABLED);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐歌手不存在");
            }
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐歌手资料暂时不可用");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐歌手资料暂时不可用");
        }
    }

    private MusicTrackBo artistTrack(JsonNode item, String artistName) {
        String songMid = item.path("songMid").asText("");
        String name = item.path("name").asText("").trim();
        if (!songMid.matches("[A-Za-z0-9]+") || !StringUtils.hasText(name)) return null;
        List<String> artists = new ArrayList<>();
        if (item.path("artists").isArray()) {
            item.path("artists").forEach(value -> {
                if (StringUtils.hasText(value.asText())) artists.add(value.asText());
            });
        }
        String albumMid = item.path("albumMid").asText("");
        String imageUrl = albumMid.matches("[A-Za-z0-9]+")
                ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg?max_age=2592000"
                : null;
        String mediaMid = item.path("mediaMid").asText(songMid);
        String playbackUrl = UriComponentsBuilder.fromPath("/api/music/qq/play/")
                .pathSegment(songMid).queryParam("mediaId", mediaMid).build().encode().toUriString();
        return new MusicTrackBo("qq:" + songMid, name, artists, item.path("album").asText(""),
                imageUrl, Math.max(0, item.path("durationMs").asLong()),
                "https://y.qq.com/n/ryqq/songDetail/" + songMid, "qq", "audio", playbackUrl, null)
                .withAlbumId(albumMid)
                .withRecommendationReason(List.of("QQ_ARTIST_PAGE"), "来自 " + artistName + " 的歌手主页", false, 1.0);
    }

    @Override
    public QqAlbumDetailBo album(long userId, UUID conversationId, String albumMid) {
        if (albumMid == null || !albumMid.matches("[A-Za-z0-9]{5,30}")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐专辑标识不正确");
        }
        if (conversationId == null) throw new AppException(HttpStatus.BAD_REQUEST, "会话标识不能为空");
        requireBridge();
        if (personalization != null) personalization.requireOwnedConversation(userId, conversationId);
        try {
            JsonNode response = sidecar.album(albumMid);
            if (response == null || !StringUtils.hasText(response.path("name").asText())) {
                throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐专辑不存在");
            }
            List<MusicTrackBo> tracks = new ArrayList<>();
            if (response.path("tracks").isArray()) {
                for (JsonNode item : response.path("tracks")) {
                    MusicTrackBo track = artistTrack(item, response.path("name").asText());
                    if (track != null) tracks.add(track);
                }
            }
            UUID exposureId = tracks.isEmpty() ? null : UUID.randomUUID();
            if (exposureId != null && personalization != null) {
                List<String> albumTags = new ArrayList<>();
                if (StringUtils.hasText(response.path("genre").asText())) {
                    albumTags.add("GENRE:" + response.path("genre").asText().trim());
                }
                if (StringUtils.hasText(response.path("language").asText())) {
                    albumTags.add("LANGUAGE:" + response.path("language").asText().trim());
                }
                List<MusicPersonalizationRepository.ExposureTrack> exposureTracks = new ArrayList<>();
                for (int index = 0; index < tracks.size(); index++) {
                    exposureTracks.add(new MusicPersonalizationRepository.ExposureTrack(tracks.get(index),
                            Map.of("qqAlbum", index + 1), Map.of("albumMid", albumMid),
                            List.of("QQ_ALBUM_PAGE"), albumTags,
                            Math.max(0.01, 1.0 - index * 0.002), false));
                }
                personalization.recordExposure(userId, conversationId, exposureId,
                        "打开 QQ 音乐专辑：" + response.path("name").asText(),
                        Map.of("intent", "QQ_ALBUM_PAGE", "albumMid", albumMid),
                        "qq-album-v1", MusicPersonalizationStatus.DISABLED, exposureTracks);
            }
            return new QqAlbumDetailBo(exposureId, albumMid, response.path("name").asText(),
                    response.path("coverUrl").asText(null), textList(response.path("artists")),
                    response.path("artistMid").asText(""), response.path("publishDate").asText(""),
                    response.path("genre").asText(""), response.path("language").asText(""),
                    response.path("company").asText(""), response.path("description").asText(""),
                    Math.max(response.path("trackCount").asInt(), tracks.size()),
                    response.path("externalUrl").asText("https://y.qq.com/n/ryqq/albumDetail/" + albumMid),
                    tracks, "qq-album-v1", MusicPersonalizationStatus.DISABLED);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐专辑不存在");
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐专辑暂时无法打开");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐专辑暂时无法打开");
        }
    }

    @Override
    public QqVideoPlaybackBo video(String videoId) {
        if (videoId == null || !videoId.matches("[A-Za-z0-9]{5,30}")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐视频标识不正确");
        }
        requireBridge();
        try {
            JsonNode response = sidecar.video(videoId);
            String url = response == null ? "" : response.path("playbackUrl").asText("");
            if (!StringUtils.hasText(url)) throw new AppException(HttpStatus.NOT_FOUND, "当前视频暂时无法播放");
            return new QqVideoPlaybackBo(videoId, url, Math.max(0, response.path("durationMs").asLong()),
                    response.path("quality").asText(""), response.path("externalUrl").asText(""));
        } catch (RestClientResponseException exception) {
            throw new AppException(exception.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY,
                    exception.getStatusCode().value() == 404 ? "当前视频暂时无法播放" : "QQ 音乐视频服务暂时不可用");
        }
    }

    private QqPublicPlaylistBo publicPlaylistSummary(JsonNode item) {
        String id = item.path("id").asText("");
        String name = item.path("name").asText("").trim();
        if (!id.matches("\\d{5,20}") || !StringUtils.hasText(name)) return null;
        List<String> tags = new ArrayList<>();
        if (item.path("tags").isArray()) {
            item.path("tags").forEach(tag -> {
                if (StringUtils.hasText(tag.asText())) tags.add(tag.asText());
            });
        }
        return new QqPublicPlaylistBo(id, name, item.path("description").asText(""),
                item.path("coverUrl").asText(null), item.path("creatorName").asText("QQ 音乐用户"),
                item.path("creatorAvatarUrl").asText(null), Math.max(0, item.path("listenCount").asLong()),
                Math.max(0, item.path("trackCount").asInt()), tags,
                item.path("externalUrl").asText("https://y.qq.com/n/ryqq/playlist/" + id),
                null, List.of(), "qq-public-v1", MusicPersonalizationStatus.DISABLED);
    }

    private MusicTrackBo publicPlaylistTrack(JsonNode item, QqPublicPlaylistBo playlist) {
        String songMid = item.path("songMid").asText("");
        String name = item.path("name").asText("").trim();
        if (!songMid.matches("[A-Za-z0-9]+") || !StringUtils.hasText(name)) return null;
        List<String> artists = new ArrayList<>();
        if (item.path("artists").isArray()) {
            item.path("artists").forEach(artist -> {
                if (StringUtils.hasText(artist.asText())) artists.add(artist.asText());
            });
        }
        String mediaMid = item.path("mediaMid").asText(songMid);
        String albumMid = item.path("albumMid").asText("");
        String imageUrl = albumMid.matches("[A-Za-z0-9]+")
                ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg?max_age=2592000"
                : playlist.coverUrl();
        String playbackUrl = UriComponentsBuilder.fromPath("/api/music/qq/play/")
                .pathSegment(songMid).queryParam("mediaId", mediaMid).build().encode().toUriString();
        return new MusicTrackBo("qq:" + songMid, name, artists, item.path("album").asText(""),
                imageUrl, Math.max(0, item.path("durationMs").asLong()),
                "https://y.qq.com/n/ryqq/songDetail/" + songMid, "qq", "audio", playbackUrl, null)
                .withAlbumId(albumMid)
                .withRecommendationReason(List.of("QQ_PUBLIC_PLAYLIST"),
                        "来自“" + playlist.name() + "” · " + playlist.creatorName(), false, 1.0);
    }

    private void requireBridge() {
        if (configuration == null || !configuration.configured() || !sidecar.healthy()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐本机 Bridge 未启动");
        }
    }

    private static List<MusicLyricLineBo> mergeLyrics(String original, String translated, String romanized) {
        ParsedLyrics primary = parseLyrics(original);
        ParsedLyrics translation = parseLyrics(translated);
        ParsedLyrics romanization = parseLyrics(romanized);
        if (!primary.synced().isEmpty()) {
            return primary.synced().entrySet().stream()
                    .map(entry -> new MusicLyricLineBo(entry.getKey(), entry.getValue(),
                            translation.synced().get(entry.getKey()), romanization.synced().get(entry.getKey())))
                    .toList();
        }
        return primary.plain().stream()
                .map(text -> new MusicLyricLineBo(null, text, null, null))
                .toList();
    }

    private static ParsedLyrics parseLyrics(String rawLyrics) {
        Map<Long, String> synced = new TreeMap<>();
        List<String> plain = new ArrayList<>();
        if (!StringUtils.hasText(rawLyrics)) {
            return new ParsedLyrics(synced, plain);
        }
        for (String rawLine : decodeEntities(rawLyrics).replace("\\r", "").split("\\n")) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line) || META_TAG.matcher(line).matches()) {
                continue;
            }
            Matcher matcher = TIME_TAG.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            while (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                String fraction = matcher.group(3);
                int millis = fraction == null ? 0 : switch (fraction.length()) {
                    case 1 -> Integer.parseInt(fraction) * 100;
                    case 2 -> Integer.parseInt(fraction) * 10;
                    default -> Integer.parseInt(fraction.substring(0, 3));
                };
                timestamps.add(minutes * 60_000L + seconds * 1_000L + millis);
            }
            String text = TIME_TAG.matcher(line).replaceAll("").trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (timestamps.isEmpty()) {
                plain.add(text);
            } else {
                timestamps.forEach(timestamp -> synced.putIfAbsent(timestamp, text));
            }
        }
        return new ParsedLyrics(synced, plain);
    }

    private static String decodeEntities(String value) {
        return value.replace("&apos;", "'")
                .replace("&quot;", "\"")
                .replace("&#58;", ":")
                .replace("&#10;", "\n")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private record ParsedLyrics(Map<Long, String> synced, List<String> plain) {
    }

    private List<String> resolvedQualities() {
        String preferred = configuration == null ? "" : configuration.defaultQuality();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (QUALITY_ORDER.contains(preferred)) {
            result.add(preferred);
        }
        int start = QUALITY_ORDER.indexOf(preferred);
        if (start < 0) {
            start = 0;
        }
        result.addAll(new ArrayList<>(QUALITY_ORDER.subList(start, QUALITY_ORDER.size())));
        return List.copyOf(result);
    }
}
