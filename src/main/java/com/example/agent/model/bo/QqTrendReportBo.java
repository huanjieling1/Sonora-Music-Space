package com.example.agent.model.bo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Clearly-labelled Sonora aggregation over persisted QQ official chart observations. */
public record QqTrendReportBo(
        String kind,
        String title,
        String window,
        String sourceType,
        String methodology,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        Instant generatedAt,
        List<ArtistTrend> artists,
        List<TrackTrend> tracks
) {
    public QqTrendReportBo {
        kind = kind == null ? "" : kind;
        title = title == null ? "" : title;
        window = window == null ? "RECENT" : window;
        sourceType = sourceType == null ? "SONORA_DERIVED_FROM_QQ_CHARTS" : sourceType;
        methodology = methodology == null ? "" : methodology;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        artists = artists == null ? List.of() : List.copyOf(artists);
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public record ArtistTrend(int rank, String artistMid, String name, String imageUrl,
                              double score, int chartedTrackCount, int bestRank,
                              List<TrackTrend> topTracks) {
        public ArtistTrend {
            topTracks = topTracks == null ? List.of() : List.copyOf(topTracks);
        }
    }

    public record TrackTrend(int rank, double score, int bestOfficialRank,
                             int chartAppearances, MusicTrackBo track) {
    }
}
