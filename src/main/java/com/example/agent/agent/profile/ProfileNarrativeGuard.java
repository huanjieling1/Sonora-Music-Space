package com.example.agent.agent.profile;

import com.example.agent.agent.contract.UserTasteContext;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Rejects profile prose that escapes the supplied music-evidence boundary. */
final class ProfileNarrativeGuard {
    private static final int MAX_LENGTH = 180;
    private static final Pattern FORBIDDEN_PERSONAL_INFERENCE = Pattern.compile(
            "年龄|性别|职业|收入|住址|居住地|政治|宗教|健康状况|疾病|婚姻|学历|人格类型");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern UNSUPPORTED_STABILITY = Pattern.compile(
            "已经形成稳定(?:结论|画像)|可以确定你的|稳定地表明");
    private static final Pattern MARKDOWN_OR_LIST = Pattern.compile(
            "(?m)^\\s{0,3}#{1,6}\\s|^\\s*[-+*]\\s|\\*\\*|__|```|\\|\\s*[-:]\\s*\\|");

    private ProfileNarrativeGuard() {
    }

    static boolean isGrounded(UserTasteContext context, String answer) {
        if (context == null || !context.hasEvidence() || !StringUtils.hasText(answer)) return false;
        String value = answer.strip();
        if (value.length() > MAX_LENGTH || FORBIDDEN_PERSONAL_INFERENCE.matcher(value).find()) return false;
        if (value.contains("\n") || value.contains("\r") || MARKDOWN_OR_LIST.matcher(value).find()) return false;
        if (!context.profileReady() && UNSUPPORTED_STABILITY.matcher(value).find()) return false;
        if (!containsEvidenceAnchor(context, value)) return false;

        Set<String> allowedNumbers = new HashSet<>();
        var matcher = NUMBER.matcher(LlmMusicProfileNarrator.packet(context, ""));
        while (matcher.find()) allowedNumbers.add(matcher.group());
        matcher = NUMBER.matcher(value);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group());
            if (number > 10 && !allowedNumbers.contains(matcher.group())) return false;
        }
        return true;
    }

    private static boolean containsEvidenceAnchor(UserTasteContext context, String answer) {
        Stream<String> signals = Stream.of(context.likes(), context.avoids(), context.labels())
                .flatMap(java.util.Collection::stream).map(UserTasteContext.Signal::value);
        Stream<String> ranks = Stream.of(context.topTracks(), context.topArtists(), context.topTags())
                .flatMap(java.util.Collection::stream).map(UserTasteContext.RankedItem::name);
        return Stream.concat(Stream.concat(signals, ranks), Stream.of(context.stageLabel(), "证据不足", "不足以"))
                .filter(StringUtils::hasText).anyMatch(answer::contains);
    }
}
