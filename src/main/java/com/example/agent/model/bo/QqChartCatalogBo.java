package com.example.agent.model.bo;

import java.time.Instant;
import java.util.List;

/** QQ Music's official chart directory, with the upstream period preserved verbatim. */
public record QqChartCatalogBo(String sourceType, Instant fetchedAt, List<Group> groups) {
    public QqChartCatalogBo {
        sourceType = sourceType == null || sourceType.isBlank() ? "QQ_OFFICIAL" : sourceType;
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public record Group(String name, List<Chart> charts) {
        public Group {
            name = name == null ? "" : name.strip();
            charts = charts == null ? List.of() : List.copyOf(charts);
        }
    }

    public record Chart(int id, String name, String group, String period, String updateTime,
                        String coverUrl, String description, int total) {
        public Chart {
            name = name == null ? "" : name.strip();
            group = group == null ? "" : group.strip();
            period = period == null ? "" : period.strip();
            updateTime = updateTime == null ? "" : updateTime.strip();
            description = description == null ? "" : description.strip();
            total = Math.max(0, total);
        }
    }
}
