package com.example.agent.agent.profile;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.ProfileAgentResult;
import com.example.agent.agent.contract.UserTasteContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MusicProfileAgent {
    private final MusicProfileContextReader contextReader;
    private final MusicProfileNarrator narrator;

    public MusicProfileAgent(MusicProfileContextReader contextReader, MusicProfileNarrator narrator) {
        this.contextReader = contextReader;
        this.narrator = narrator;
    }

    public ProfileAgentResult analyze(MusicAgentTurn turn) {
        UserTasteContext context = contextReader.read(turn.userId());
        try {
            String answer = narrator.narrate(context, turn.request());
            if (StringUtils.hasText(answer) && ProfileNarrativeGuard.isGrounded(context, answer)) {
                return new ProfileAgentResult(context, answer, true);
            }
        } catch (RuntimeException ignored) {
            // A readable evidence-only result is safer than failing the entire turn.
        }
        return new ProfileAgentResult(context, fallback(context), false);
    }

    static String fallback(UserTasteContext context) {
        if (!context.hasEvidence()) {
            return "你的音乐画像还在等待第一束光。目前的听歌证据不多，再完整听过几段旋律或认真留下一个喜欢，它就会慢慢显影。";
        }
        String anchor = !context.topArtists().isEmpty() ? context.topArtists().get(0).name()
                : !context.topTags().isEmpty() ? context.topTags().get(0).name()
                : !context.labels().isEmpty() ? context.labels().get(0).value() : context.stageLabel();
        StringBuilder result = new StringBuilder("你此刻最清晰的音乐坐标是「").append(anchor).append("」");
        if (!context.topArtists().isEmpty()) {
            result.append("，你与这位歌手相遇了 ").append(context.topArtists().get(0).count()).append(" 次");
        } else if (!context.topTags().isEmpty()) {
            result.append("，它在现有记录里回响了 ").append(context.topTags().get(0).count()).append(" 次");
        }
        result.append("。");
        if (!context.profileReady()) {
            result.append("画像仍在显影，新的完播、跳过与重逢会继续改变它的方向。");
        } else {
            result.append("这幅画像已有轮廓，但下一首歌仍能为它添上新的颜色。");
        }
        return result.toString();
    }
}
