package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Extensible acceptance policy contributed by each child-agent role. */
public interface MusicTaskAcceptanceCriteriaContributor {
    boolean supports(MusicWorkflowTaskSpec task);
    List<String> criteria(MusicWorkflowTaskSpec task, MusicIntentUnderstanding understanding);
}

@Component
final class IntentAcceptanceCriteriaContributor implements MusicTaskAcceptanceCriteriaContributor {
    @Override public boolean supports(MusicWorkflowTaskSpec task) { return "Intent Agent".equals(task.assignedAgent()); }
    @Override public List<String> criteria(MusicWorkflowTaskSpec task, MusicIntentUnderstanding understanding) {
        return List.of("目标、实体、关系和硬约束已经转为结构化意图");
    }
}

@Component
final class ProfileAcceptanceCriteriaContributor implements MusicTaskAcceptanceCriteriaContributor {
    @Override public boolean supports(MusicWorkflowTaskSpec task) {
        return task.assignedAgent().contains("Profile Agent");
    }
    @Override public List<String> criteria(MusicWorkflowTaskSpec task, MusicIntentUnderstanding understanding) {
        return List.of("画像只能来自当前用户可审计的收听行为");
    }
}

@Component
final class EvaluationAcceptanceCriteriaContributor implements MusicTaskAcceptanceCriteriaContributor {
    @Override public boolean supports(MusicWorkflowTaskSpec task) { return "Evaluator".equals(task.assignedAgent()); }
    @Override public List<String> criteria(MusicWorkflowTaskSpec task, MusicIntentUnderstanding understanding) {
        return List.of("结果类型必须与原始请求目标一致", "每项成功结论必须具有真实工具证据");
    }
}

@Component
final class ResponseAcceptanceCriteriaContributor implements MusicTaskAcceptanceCriteriaContributor {
    @Override public boolean supports(MusicWorkflowTaskSpec task) {
        return task.assignedAgent().contains("Response Agent");
    }
    @Override public List<String> criteria(MusicWorkflowTaskSpec task, MusicIntentUnderstanding understanding) {
        return List.of("回复不得增加未通过验收的歌曲、歌单或执行动作");
    }
}

@Component
final class ExecutionAcceptanceCriteriaContributor implements MusicTaskAcceptanceCriteriaContributor {
    @Override public boolean supports(MusicWorkflowTaskSpec task) { return "Execution Agent".equals(task.assignedAgent()); }
    @Override public List<String> criteria(MusicWorkflowTaskSpec task, MusicIntentUnderstanding understanding) {
        ArrayList<String> criteria = new ArrayList<>();
        criteria.add("执行结果必须回应用户原始目标");
        if (understanding == null) return criteria;
        MusicIntentDraft intent = understanding.intent();
        if (intent.target() == MusicIntentDraft.Target.PLAYLIST) {
            if (understanding.route() == com.example.agent.agent.contract.MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST) {
                criteria.add("必须加载真实 QQ 音乐公开歌单曲目并建立播放队列");
                criteria.add("完整成功必须验证一首可播放歌曲；若账号无可播放曲目则保留队列并明确部分成功");
            } else {
                criteria.add("必须返回真实 QQ 音乐歌单卡片及可验证歌单标识");
            }
        } else if (intent.target() == MusicIntentDraft.Target.ARTIST) {
            criteria.add("必须返回真实 QQ 音乐艺人卡片及来源资料");
            if (understanding.route() == com.example.agent.agent.contract.MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE) {
                criteria.add("查询关键词必须是画像证据解析出的歌手实体，禁止透传用户整句请求");
            }
        } else if (intent.mode() == MusicIntentDraft.Mode.TRENDING) {
            criteria.add("必须包含榜单来源、统计周期和排名依据");
        } else if (intent.target() == MusicIntentDraft.Target.TRACK
                || intent.target() == MusicIntentDraft.Target.ALBUM) {
            criteria.add("必须返回真实歌曲卡片或经过验证的播放动作");
        }
        return criteria;
    }
}
