package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicSupportContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Hybrid emotional-context parser: safety rules are deterministic and language understanding is model-backed. */
@Component
public class MusicSupportContextAgent {
    private static final Pattern SAFETY = Pattern.compile(
            "自杀|轻生|结束生命|不想活|活不下去|伤害自己|自残|跳楼|割腕|suicid|kill myself|hurt myself",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SADNESS = Pattern.compile(
            "不开心|难过|伤心|心情不好|情绪低落|想哭|失落|沮丧|很丧|痛苦|崩溃");
    private static final Pattern LONELINESS = Pattern.compile("孤独|寂寞|没人陪|没有人懂|一个人很难熬");
    private static final Pattern STRESS = Pattern.compile("压力(?:很|太)?大|压得喘不过气|工作压|学习压|好累|心累");
    private static final Pattern ANXIETY = Pattern.compile("焦虑|紧张|心慌|烦躁|静不下来|很不安");
    private static final Pattern SLEEPLESS = Pattern.compile("睡不着|失眠|无法入睡|彻夜未眠");
    private static final Pattern FATIGUE = Pattern.compile("疲惫|很累|太累|没精神|精疲力尽");
    private static final Pattern LOW_ENERGY = Pattern.compile("没动力|提不起劲|没有干劲|不想动|没力气");
    private static final Pattern CELEBRATION = Pattern.compile(
            "太开心|好开心|很开心|有点开心|挺开心|(?<!不)开心(?:了|呀|啊)?|高兴|快乐|喜悦|心情(?:很|挺|非常|特别)?不错|"
                    + "值得庆祝|成功了|终于完成|太棒了");

    private final MusicSupportContextInterpreter interpreter;

    public MusicSupportContextAgent() {
        this(null);
    }

    @Autowired
    public MusicSupportContextAgent(MusicSupportContextInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public MusicSupportContext analyze(MusicAgentTurn turn) {
        String request = turn == null || turn.request() == null ? "" : turn.request().strip();
        if (request.isBlank()) return MusicSupportContext.none();
        if (SAFETY.matcher(request).find()) {
            return new MusicSupportContext(MusicSupportContext.InteractionType.SAFETY_CONCERN,
                    MusicSupportContext.EmotionalSignal.SADNESS, MusicSupportContext.SupportGoal.SAFETY,
                    1, "");
        }
        MusicSupportContext fallback = deterministic(request);
        MusicSupportContext model = interpreter == null ? null : interpreter.understand(request).orElse(null);
        if (model == null) return fallback;
        if (model.safetyConcern() && model.confidence() < 0.9) return fallback;
        if (fallback.actionable() && model.interactionType() == MusicSupportContext.InteractionType.NONE) {
            return fallback;
        }
        return model.actionable() || model.safetyConcern() ? merge(fallback, model) : fallback;
    }

    private static MusicSupportContext deterministic(String request) {
        if (SLEEPLESS.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.SLEEPLESSNESS,
                MusicSupportContext.SupportGoal.SOOTHE, "安静、舒缓、低刺激，适合慢慢入睡");
        if (LONELINESS.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.LONELINESS,
                MusicSupportContext.SupportGoal.ACCOMPANY, "温暖、有陪伴感，不过分煽情");
        if (ANXIETY.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.ANXIETY,
                MusicSupportContext.SupportGoal.SOOTHE, "节奏平稳、呼吸感宽松、不过度刺激");
        if (STRESS.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.STRESS,
                MusicSupportContext.SupportGoal.SOOTHE, "舒展、柔和，帮助从紧绷中缓下来");
        if (FATIGUE.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.FATIGUE,
                MusicSupportContext.SupportGoal.ACCOMPANY, "轻柔但不沉重，适合疲惫时陪伴");
        if (LOW_ENERGY.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.LOW_ENERGY,
                MusicSupportContext.SupportGoal.ENERGIZE, "由平缓渐进到明亮，带来一点力量");
        if (CELEBRATION.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.CELEBRATION,
                MusicSupportContext.SupportGoal.ENERGIZE, "明亮、有律动感，适合分享喜悦");
        if (SADNESS.matcher(request).find()) return support(MusicSupportContext.EmotionalSignal.SADNESS,
                MusicSupportContext.SupportGoal.SOOTHE, "温柔、舒缓、不过分悲伤，适合安静陪伴");
        return MusicSupportContext.none();
    }

    private static MusicSupportContext support(MusicSupportContext.EmotionalSignal signal,
                                                MusicSupportContext.SupportGoal goal, String direction) {
        return new MusicSupportContext(MusicSupportContext.InteractionType.SUPPORT_SEEKING,
                signal, goal, 0.86, direction);
    }

    private static MusicSupportContext merge(MusicSupportContext fallback, MusicSupportContext model) {
        if (!fallback.actionable()) return model;
        return new MusicSupportContext(model.interactionType(),
                model.signal() == MusicSupportContext.EmotionalSignal.NONE ? fallback.signal() : model.signal(),
                model.goal() == MusicSupportContext.SupportGoal.NONE ? fallback.goal() : model.goal(),
                Math.max(fallback.confidence(), model.confidence()),
                model.musicDirection().isBlank() ? fallback.musicDirection() : model.musicDirection());
    }
}
