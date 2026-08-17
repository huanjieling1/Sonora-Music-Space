package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.QqArtistDetailBo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QqArtistProfileSummarizerTest {
    @Test
    void extractsOnlyGroundedAchievementAndStyleEvidence() {
        var detail = detail("这支乐队于 2010 年成立。作品获得年度音乐奖提名。"
                + "他们的曲风融合电子、摇滚与古典音乐。", 88, 9);

        var summary = QqArtistProfileSummarizer.summarize(detail);

        assertThat(summary.biography()).contains("2010 年成立", "年度音乐奖提名");
        assertThat(summary.achievements()).contains("年度音乐奖提名", "88 首歌曲", "9 张专辑");
        assertThat(summary.style()).contains("电子", "摇滚", "古典");
    }

    @Test
    void explicitlyDeclinesToInventAwardsOrGenresWhenSourceIsMissing() {
        var summary = QqArtistProfileSummarizer.summarize(detail("这是一位音乐创作者。", 10, 2));

        assertThat(summary.achievements()).contains("10 首歌曲", "2 张专辑", "未列出可核验的具体奖项");
        assertThat(summary.style()).contains("当前不根据歌曲名或封面猜测");
    }

    private static QqArtistDetailBo detail(String description, int songs, int albums) {
        return new QqArtistDetailBo(null, "001artist", "测试艺人", null, "", "", "", description,
                "https://y.qq.com/artist", songs, albums, 1, 12, songs > 12, 1, 8, albums > 8,
                List.of(), List.of(), "qq-artist-v1", MusicPersonalizationStatus.DISABLED);
    }
}
