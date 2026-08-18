package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicToolName;
import com.example.agent.service.MusicKeywordExtractor;
import com.example.agent.service.MusicQueryPlanner;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MusicKeywordExtractorImpl implements MusicKeywordExtractor {
    private final MusicQueryPlanner queryPlanner;
    private final MusicSearchPlanCompiler planCompiler;

    public MusicKeywordExtractorImpl(MusicQueryPlanner queryPlanner,
                                     MusicSearchPlanCompiler planCompiler) {
        this.queryPlanner = queryPlanner;
        this.planCompiler = planCompiler;
    }

    @Override
    public ExtractedKeyword extract(String description) {
        String original = MusicTextNormalizer.cleanRequest(description);
        MusicSearchPlan proposed = queryPlanner.plan(original);
        MusicExecutionPlan execution = planCompiler.compile(original, proposed);
        MusicSearchTask direct = execution.tool(MusicToolName.QQ_DIRECT_SEARCH)
                .flatMap(call -> call.tasks().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("音乐执行计划缺少 QQ 直搜关键词"));
        String keyword = direct.query();
        if (!StringUtils.hasText(keyword) || !isLiteralFragment(original, keyword)) {
            keyword = MusicTextNormalizer.primarySearchQuery(original);
        }
        return new ExtractedKeyword(keyword.strip(), execution.intent(),
                planCompiler.understanding(execution, List.of()), proposed);
    }

    /**
     * The language model may classify the intent, but it must not silently turn an
     * acronym or nickname into a different catalog query. Only text that was
     * literally present in the request may reach QQ Music.
     */
    private boolean isLiteralFragment(String original, String keyword) {
        String normalizedOriginal = MusicTextNormalizer.normalize(original);
        String normalizedKeyword = MusicTextNormalizer.normalize(keyword);
        return StringUtils.hasText(normalizedKeyword) && normalizedOriginal.contains(normalizedKeyword);
    }
}
