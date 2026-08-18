package com.example.agent.model.bo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One official QQ Music chart period. Rankings remain source facts and are never inferred by the LLM. */
public record QqChartDetailBo(
        String sourceType,
        Instant fetchedAt,
        QqChartCatalogBo.Chart chart,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        int offset,
        int pageSize,
        boolean hasNext,
        List<Entry> entries
) {
    public QqChartDetailBo {
        sourceType = sourceType == null || sourceType.isBlank() ? "QQ_OFFICIAL" : sourceType;
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
        entries = entries == null ? List.of() : List.copyOf(entries);
        offset = Math.max(0, offset);
        pageSize = Math.max(1, pageSize);
    }

    public record Entry(int rank, int rankType, String rankValue, List<String> singerMids,
                        MusicTrackBo track) {
        public Entry {
            rank = Math.max(1, rank);
            rankType = Math.max(0, rankType);
            rankValue = rankValue == null ? "0" : rankValue;
            singerMids = singerMids == null ? List.of() : List.copyOf(singerMids);
        }
    }
}
