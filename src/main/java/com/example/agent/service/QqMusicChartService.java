package com.example.agent.service;

import com.example.agent.model.bo.QqChartCatalogBo;
import com.example.agent.model.bo.QqChartDetailBo;
import com.example.agent.model.bo.QqTrendReportBo;

public interface QqMusicChartService {
    QqChartCatalogBo catalog();

    QqChartDetailBo chart(int chartId, String period, int offset, int limit);

    QqTrendReportBo trendingArtists(String window, String group, int limit);

    QqTrendReportBo artistTopTracks(String artistMid, String artistName, String window, int limit);
}
