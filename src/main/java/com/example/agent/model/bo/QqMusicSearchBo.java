package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

public record QqMusicSearchBo(
        UUID searchId,
        String keyword,
        QqMusicSearchType type,
        int page,
        int pageSize,
        long total,
        boolean hasNext,
        List<MusicTrackBo> tracks,
        List<Artist> artists,
        List<Album> albums,
        List<Playlist> playlists,
        List<Video> videos,
        List<Lyric> lyrics,
        List<User> users
) {
    public QqMusicSearchBo {
        tracks = copy(tracks);
        artists = copy(artists);
        albums = copy(albums);
        playlists = copy(playlists);
        videos = copy(videos);
        lyrics = copy(lyrics);
        users = copy(users);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Artist(String id, String mid, String name, String imageUrl,
                         long songCount, long albumCount, long videoCount, String externalUrl) {}

    public record Album(String id, String mid, String name, String coverUrl, List<String> artists,
                        String publishDate, long trackCount, String externalUrl) {
        public Album { artists = copy(artists); }
    }

    public record Playlist(String id, String name, String description, String coverUrl,
                           String creatorName, long listenCount, long trackCount, String externalUrl) {}

    public record Video(String id, String name, String coverUrl, List<String> artists,
                        long durationMs, long playCount, String publishDate, String externalUrl) {
        public Video { artists = copy(artists); }
    }

    public record Lyric(MusicTrackBo track, String snippet) {}

    public record User(String id, String name, String avatarUrl, long followerCount,
                       long playlistCount, String badge, String externalUrl) {}
}
