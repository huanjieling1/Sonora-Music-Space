package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicTaskEvaluation;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.orchestration.workflow.MusicWorkflowHandlerRegistry;
import com.example.agent.orchestration.workflow.MusicWorkflowPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Hybrid evaluator: hard business facts are checked here before any response is produced. */
@Component
public class MusicTaskEvaluator {
    private final MusicWorkflowHandlerRegistry handlers;

    /** Compatibility constructor for focused tests that do not start Spring. */
    public MusicTaskEvaluator() {
        this(MusicWorkflowHandlerRegistry.builtIns());
    }

    @Autowired
    public MusicTaskEvaluator(MusicWorkflowHandlerRegistry handlers) {
        this.handlers = handlers;
    }

    public MusicTaskEvaluation evaluate(MusicExecutionResult result) {
        return evaluate(result, null);
    }

    public MusicTaskEvaluation evaluate(MusicExecutionResult result, MusicIntentUnderstanding understanding) {
        if (result == null) return MusicTaskEvaluation.revise("执行 Agent 没有返回结构化结果",
                "必须返回 TaskResult，并明确成功状态、事实结果和证据类型");
        if (result.successful() && StringUtils.hasText(result.factualAnswer())) {
            String mismatch = evidenceMismatch(result, understanding);
            return mismatch == null ? MusicTaskEvaluation.pass()
                    : correctionForMismatch(result.route(), mismatch);
        }
        String reason = StringUtils.hasText(result.factualAnswer())
                ? result.factualAnswer() : "没有得到可验证的结果";
        boolean retryable = handlers.policy(result.route()).retryable() && isTransient(reason);
        return retryable ? MusicTaskEvaluation.revise(reason, "使用同一原始目标重新调用真实数据源")
                : MusicTaskEvaluation.fail(reason);
    }

    public MusicTaskEvaluation evaluateSupport(MusicExecutionResult result, AgentActionType expectedEvidence) {
        MusicTaskEvaluation base = evaluate(result, null);
        if (!base.passed()) return base;
        if (expectedEvidence == null || result.evidenceTypes().contains(expectedEvidence)) {
            return MusicTaskEvaluation.pass();
        }
        return MusicTaskEvaluation.revise(
                "主动建议没有产生声明的真实结果类型：" + expectedEvidence,
                "必须执行声明的音乐能力并返回 " + expectedEvidence + " 类型证据");
    }

    private String evidenceMismatch(MusicExecutionResult result, MusicIntentUnderstanding understanding) {
        MusicAgentRoute route = result.route();
        java.util.Set<AgentActionType> types = result.evidenceTypes();
        if (route == MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST) {
            return randomPlaylistMismatch(result, types);
        }
        if (types == null || types.isEmpty()) return null;
        if (understanding != null) {
            MusicIntentDraft.Target target = understanding.intent().target();
            if (target == MusicIntentDraft.Target.PLAYLIST
                    && !types.contains(AgentActionType.SHOW_QQ_PLAYLIST_RESULTS)) {
                return "结果类型不符合原始意图：用户要求歌单，但执行结果没有歌单卡片";
            }
            if (target == MusicIntentDraft.Target.ARTIST
                    && !types.contains(AgentActionType.SHOW_QQ_ARTIST_RESULTS)) {
                return "结果类型不符合原始意图：用户要求歌手资料，但执行结果没有歌手卡片";
            }
            if ((target == MusicIntentDraft.Target.TRACK || target == MusicIntentDraft.Target.ALBUM)
                    && resultRouteNeedsMusic(understanding.route())
                    && !types.contains(AgentActionType.SHOW_MUSIC_RESULTS)) {
                return "结果类型不符合原始意图：用户要求歌曲，但执行结果没有真实歌曲卡片";
            }
            if (understanding.intent().mode() == MusicIntentDraft.Mode.TRENDING) {
                return types.contains(AgentActionType.SHOW_QQ_CHART_RESULTS) ? null
                        : "趋势请求缺少榜单来源、统计周期和排名依据，不能按普通搜索结果验收";
            }
        }
        MusicWorkflowPolicy policy = handlers.policy(route);
        if (!policy.requiredEvidence().isEmpty()
                && policy.requiredEvidence().stream().noneMatch(types::contains)) {
            return "执行结果缺少工作流策略要求的真实证据：" + policy.requiredEvidence();
        }
        return null;
    }

    private static String randomPlaylistMismatch(MusicExecutionResult result,
                                                 java.util.Set<AgentActionType> types) {
        java.util.Set<AgentActionType> actual = types == null ? java.util.Set.of() : types;
        if (!actual.contains(AgentActionType.SHOW_MUSIC_RESULTS)) {
            return "随机歌单没有返回真实歌曲列表";
        }
        if (!actual.contains(AgentActionType.QUEUE_MUSIC_RESULTS)) {
            return "随机歌单没有建立可操作的播放队列";
        }
        if (!result.partial() && !actual.contains(AgentActionType.PLAY_TRACK)) {
            return "随机歌单声称完整成功，但没有经过验证的播放动作";
        }
        return null;
    }

    private static boolean resultRouteNeedsMusic(MusicAgentRoute route) {
        return route == MusicAgentRoute.MUSIC_DISCOVERY || route == MusicAgentRoute.RESULT_NAVIGATION;
    }

    private MusicTaskEvaluation correctionForMismatch(MusicAgentRoute route, String mismatch) {
        boolean retryable = handlers.policy(route).retryable();
        if (!retryable) return MusicTaskEvaluation.fail(mismatch);
        String correction = mismatch.contains("歌单") ? "只返回与目标实体或场景相关的真实歌单卡片"
                : mismatch.contains("歌手") ? "只返回匹配目标艺人的真实档案卡片"
                : mismatch.contains("榜单") || mismatch.contains("趋势")
                ? "改用 QQ 音乐官方榜单能力并附带榜单周期和来源"
                : "重新执行正确能力，并返回与原始目标一致的结构化结果卡片";
        return MusicTaskEvaluation.revise(mismatch, correction);
    }

    private static boolean isTransient(String reason) {
        return reason.contains("暂时") || reason.contains("超时") || reason.contains("失败")
                || reason.contains("不可用") || reason.contains("网络");
    }
}
