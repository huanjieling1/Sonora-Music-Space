package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicIntentEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts high-impact routing evidence from the current turn without using a language model. */
@Component
public final class MusicIntentEvidenceExtractor {
    private static final Pattern MUSIC_ACTION = Pattern.compile(
            "推荐|搜索|搜一下|查找|找(?:些|点|一首|一批)?|来点|来些|来一首|想听|播放|放一首|加入队列|"
                    + "分析|总结|查看|换一批|重新推荐|recommend|search|find|play|listen",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MUSIC_TARGET = Pattern.compile(
            "歌|歌曲|音乐|曲目|歌单|播放列表|歌手|艺人|乐队|专辑|榜单|排行榜|音乐画像|音乐偏好|"
                    + "song|music|track|playlist|artist|album|chart",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TREND = Pattern.compile(
            "热度|热门|最火|火的|排行榜|排行|榜单|飙升|上升最快|涨得最快|流行指数|热歌|新歌|最新发行|"
                    + "trending|chart|hottest|most popular|rising|new releases?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMOTION = Pattern.compile(
            "开心|高兴|快乐|喜悦|心情不错|难过|伤心|不开心|孤独|寂寞|压力|焦虑|紧张|心慌|烦躁|"
                    + "疲惫|很累|太累|心累|睡不着|失眠|没动力|提不起劲|没有干劲",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFETY = Pattern.compile(
            "不想活|想死|自杀|伤害自己|结束生命|活不下去|轻生|立即危险",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "这些|这批|刚才|上批|换一批|重新推荐|再推荐|更喜欢|偏爱|不喜欢|不想听|不合口味|不对胃口",
            Pattern.CASE_INSENSITIVE);

    public MusicIntentEvidence extract(String request) {
        String value = request == null ? "" : request.strip();
        boolean action = MUSIC_ACTION.matcher(value).find();
        boolean target = MUSIC_TARGET.matcher(value).find();
        boolean trend = TREND.matcher(value).find();
        boolean emotional = EMOTION.matcher(value).find();
        boolean safety = SAFETY.matcher(value).find();
        boolean followUp = FOLLOW_UP.matcher(value).find();
        ArrayList<String> terms = new ArrayList<>();
        collect(value, MUSIC_ACTION, "action", terms);
        collect(value, MUSIC_TARGET, "target", terms);
        collect(value, TREND, "trend", terms);
        collect(value, EMOTION, "emotion", terms);
        collect(value, SAFETY, "safety", terms);
        collect(value, FOLLOW_UP, "follow-up", terms);
        return new MusicIntentEvidence(action, target, trend, emotional, safety, followUp, List.copyOf(terms));
    }

    private static void collect(String request, Pattern pattern, String type, List<String> target) {
        Matcher matcher = pattern.matcher(request);
        if (matcher.find()) target.add(type + ":" + matcher.group());
    }
}
