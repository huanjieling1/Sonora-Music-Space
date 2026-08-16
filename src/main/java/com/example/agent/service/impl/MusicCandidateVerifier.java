package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicMatchType;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicTrackRelationBo;
import com.example.agent.model.bo.MusicUnderstandingBo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.example.agent.service.impl.MusicCandidateRanker.Candidate;

@Component
public class MusicCandidateVerifier {
    /** Validate candidates produced by the new deterministic execution contract. */
    public List<Candidate> verify(MusicExecutionPlan plan, MusicUnderstandingBo understanding,
                                  List<Candidate> candidates, boolean relatedPass) {
        if (plan == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> rejected = understanding == null ? Set.of() : new HashSet<>(understanding.rejectedTrackIds());
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            MusicTrackBo track = candidate == null ? null : candidate.track();
            if (track == null || rejected.contains(track.id()) || containsAvoidedTerm(plan, track)) {
                continue;
            }
            Match match = classify(plan, candidate, relatedPass);
            if (match != null) {
                result.add(new Candidate(track.withMatch(match.type(), match.label(), match.score()),
                        candidate.task(), candidate.providerOrder(), candidate.sequence()));
            }
        }
        return List.copyOf(result);
    }

    public List<Candidate> verify(MusicSearchPlan plan, MusicUnderstandingBo understanding,
                                  List<Candidate> candidates, boolean relatedPass) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> rejected = understanding == null ? Set.of() : new HashSet<>(understanding.rejectedTrackIds());
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            MusicTrackBo track = candidate.track();
            if (track == null || rejected.contains(track.id())) {
                continue;
            }
            Match match = classify(plan, understanding, track, relatedPass);
            if (match == null) {
                continue;
            }
            result.add(new Candidate(track.withMatch(match.type(), match.label(), match.score()),
                    candidate.task(), candidate.providerOrder(), candidate.sequence()));
        }
        return List.copyOf(result);
    }

    private Match classify(MusicSearchPlan plan, MusicUnderstandingBo understanding,
                           MusicTrackBo track, boolean relatedPass) {
        if (understanding != null && understanding.resolved()) {
            if (relatedPass) {
                return new Match(MusicMatchType.RELATED,
                        "基于 " + understanding.canonicalName() + " 的风格延伸", 0.48);
            }
            Match relation = relationMatch(understanding.trackRelations(), track);
            if (relation != null) {
                return relation;
            }
            String metadata = MusicTextNormalizer.normalize(track.name() + " "
                    + String.join(" ", track.artists()) + " " + track.album());
            boolean aliasMatch = understanding.aliases().stream()
                    .map(MusicTextNormalizer::normalize)
                    .filter(alias -> alias.length() > 1)
                    .anyMatch(metadata::contains);
            if (aliasMatch) {
                return new Match(MusicMatchType.VERIFIED, "实体元数据直接匹配", 0.9);
            }
            return null;
        }

        if (plan.intent() == MusicSearchIntent.DISCOVERY || plan.intent() == MusicSearchIntent.SIMILAR) {
            return new Match(MusicMatchType.RELATED, "符合检索风格", 0.55);
        }
        boolean titleMatch = entityMatches(track.name(), plan.track());
        boolean artistMatch = plan.artists().isEmpty() || track.artists().stream()
                .anyMatch(artist -> entityMatches(artist, plan.artists().get(0)));
        boolean albumMatch = entityMatches(track.album(), plan.album());
        if ((StringUtils.hasText(plan.track()) && titleMatch && artistMatch)
                || (StringUtils.hasText(plan.album()) && albumMatch)
                || (!plan.artists().isEmpty() && artistMatch)) {
            return new Match(MusicMatchType.VERIFIED, "名称与艺人信息匹配", 0.92);
        }
        return new Match(MusicMatchType.RELATED, "搜索结果延伸", 0.45);
    }

    private Match classify(MusicExecutionPlan plan, Candidate candidate, boolean relatedPass) {
        MusicTrackBo track = candidate.track();
        String requestedArtist = plan.hardConstraints().artists().isEmpty()
                ? null : plan.hardConstraints().artists().get(0);
        boolean titleMatch = entityMatches(track.name(), plan.hardConstraints().track());
        boolean artistMatch = !StringUtils.hasText(requestedArtist) || track.artists().stream()
                .anyMatch(artist -> entityMatches(artist, requestedArtist));
        boolean albumMatch = entityMatches(track.album(), plan.hardConstraints().album());

        switch (plan.intent()) {
            case EXACT_TRACK -> {
                if (!StringUtils.hasText(plan.hardConstraints().track()) || !titleMatch || !artistMatch) {
                    return null;
                }
                return new Match(relatedPass ? MusicMatchType.RELATED : MusicMatchType.VERIFIED,
                        relatedPass ? "精确歌曲的补充版本" : "歌名与歌手匹配", relatedPass ? 0.7 : 0.98);
            }
            case ARTIST -> {
                if (!StringUtils.hasText(requestedArtist) || !artistMatch) {
                    return null;
                }
                return new Match(relatedPass ? MusicMatchType.RELATED : MusicMatchType.VERIFIED,
                        relatedPass ? "歌手搜索补充结果" : "歌手名称匹配", relatedPass ? 0.72 : 0.97);
            }
            case ALBUM -> {
                if (!StringUtils.hasText(plan.hardConstraints().album()) || !albumMatch || !artistMatch) {
                    return null;
                }
                return new Match(relatedPass ? MusicMatchType.RELATED : MusicMatchType.VERIFIED,
                        relatedPass ? "专辑搜索补充结果" : "专辑名称匹配", relatedPass ? 0.72 : 0.96);
            }
            case ENTITY_RELATED -> {
                String metadata = metadata(track);
                String query = MusicTextNormalizer.normalize(candidate.task().query());
                if (!relatedPass && query.length() > 1 && metadata.contains(query)) {
                    return new Match(MusicMatchType.VERIFIED, "曲库元数据包含该作品或实体", 0.9);
                }
                return new Match(MusicMatchType.RELATED,
                        relatedPass ? "保守扩展搜索候选" : "曲库直接搜索候选（非官方关系证明）",
                        relatedPass ? 0.5 : 0.66);
            }
            case DISCOVERY -> {
                return new Match(relatedPass ? MusicMatchType.RELATED : MusicMatchType.VERIFIED,
                        relatedPass ? "氛围与场景补充结果" : "曲库按当前描述直接搜索", relatedPass ? 0.54 : 0.78);
            }
            case SIMILAR -> {
                return new Match(MusicMatchType.RELATED,
                        relatedPass ? "相似方向扩展结果" : "曲库相似搜索候选", relatedPass ? 0.52 : 0.68);
            }
            case AMBIGUOUS -> {
                String query = MusicTextNormalizer.normalize(candidate.task().query());
                if (query.length() > 1 && metadata(track).contains(query)) {
                    return new Match(MusicMatchType.VERIFIED, "名称直接匹配", 0.82);
                }
                return null;
            }
        }
        return null;
    }

    private static boolean containsAvoidedTerm(MusicExecutionPlan plan, MusicTrackBo track) {
        String metadata = metadata(track);
        return plan.softIntent().avoid().stream()
                .map(MusicTextNormalizer::normalize)
                .filter(value -> value.length() > 1)
                .anyMatch(metadata::contains);
    }

    private static String metadata(MusicTrackBo track) {
        return MusicTextNormalizer.normalize(track.name() + " "
                + String.join(" ", track.artists()) + " " + track.album());
    }

    private static Match relationMatch(List<MusicTrackRelationBo> relations, MusicTrackBo track) {
        String actualTitle = MusicTextNormalizer.normalize(track.name());
        String actualArtists = MusicTextNormalizer.normalize(String.join(" ", track.artists()));
        for (MusicTrackRelationBo relation : relations) {
            String expectedTitle = MusicTextNormalizer.normalize(relation.trackTitle());
            if (!actualTitle.equals(expectedTitle)
                    && !actualTitle.contains(expectedTitle) && !expectedTitle.contains(actualTitle)) {
                continue;
            }
            String expectedArtist = MusicTextNormalizer.normalize(relation.artistName());
            if (StringUtils.hasText(expectedArtist) && !actualArtists.contains(expectedArtist)) {
                continue;
            }
            return new Match(MusicMatchType.VERIFIED, relation.relationLabel(),
                    Math.max(0.8, Math.min(1, relation.confidence())));
        }
        return null;
    }

    private static boolean entityMatches(String actual, String expected) {
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected)) return false;
        String left = MusicTextNormalizer.normalize(actual);
        String right = MusicTextNormalizer.normalize(expected);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private record Match(MusicMatchType type, String label, double score) {
    }
}
