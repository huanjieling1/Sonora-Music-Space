package com.example.agent.model.bo;

/** Trusted chart evidence rendered by chat. Exactly one payload is normally present. */
public record QqChartResultBo(QqChartDetailBo officialChart, QqTrendReportBo trendReport) {
    public static QqChartResultBo official(QqChartDetailBo chart) {
        return new QqChartResultBo(chart, null);
    }

    public static QqChartResultBo trend(QqTrendReportBo report) {
        return new QqChartResultBo(null, report);
    }
}
