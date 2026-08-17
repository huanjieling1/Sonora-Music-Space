package com.example.agent.service.impl;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic recovery for explicit music requests missed by model tool routing. */
final class MusicRequestFallback {
    private static final Pattern PLAYLIST_SUBJECT = Pattern.compile(
            "歌单|播放列表|playlist",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RANDOM_PLAYLIST_ACTION = Pattern.compile(
            "随机|随便|任意|抽(?:取|一个|一份)?|random|shuffle",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYLIST_CREATION_ACTION = Pattern.compile(
            "创建|新建|生成|制作|定制|做(?:一个|一份)|create|generate",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYLIST_SEARCH_ACTION = Pattern.compile(
            "搜索|搜一下|查找|找|推荐|给我|来个|来份|相关|search|find|recommend",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_SUBJECT = Pattern.compile(
            "歌手|艺人|乐队|组合|音乐人|创作人|composer|singer|artist|band|group",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_LOOKUP_ACTION = Pattern.compile(
            "搜索|搜一下|查找|找|介绍|了解|资料|档案|是谁|生涯|作品|search|find|introduce|profile|about",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MUSIC_SUBJECT = Pattern.compile(
            "歌|歌曲|音乐|曲目|歌手|专辑|歌单|主题曲|原声|配乐|ost|soundtrack|song|music|track|artist|album|playlist",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DISCOVERY_ACTION = Pattern.compile(
            "搜索|搜一下|查找|找|推荐|给我|来点|来首|想听|听一下|听点|播放|放一首|放点|search|find|recommend|listen|play",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYBACK_ACTION = Pattern.compile(
            "想听|给我听|听一下|听点|开始听|播放|放一首|放点|listen|play",
            Pattern.CASE_INSENSITIVE);

    private MusicRequestFallback() {
    }

    static boolean shouldPlayRandomQqPublicPlaylist(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        return PLAYLIST_SUBJECT.matcher(normalized).find()
                && RANDOM_PLAYLIST_ACTION.matcher(normalized).find()
                && !PLAYLIST_CREATION_ACTION.matcher(normalized).find();
    }

    static boolean shouldSearchQqPlaylists(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        return PLAYLIST_SUBJECT.matcher(normalized).find()
                && PLAYLIST_SEARCH_ACTION.matcher(normalized).find()
                && !RANDOM_PLAYLIST_ACTION.matcher(normalized).find()
                && !PLAYLIST_CREATION_ACTION.matcher(normalized).find();
    }

    static boolean shouldSearchQqArtists(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        return ARTIST_SUBJECT.matcher(normalized).find()
                && ARTIST_LOOKUP_ACTION.matcher(normalized).find()
                && !PLAYLIST_SUBJECT.matcher(normalized).find()
                && !PLAYBACK_ACTION.matcher(normalized).find();
    }

    static boolean shouldSearch(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        if (!DISCOVERY_ACTION.matcher(normalized).find()) return false;
        return MUSIC_SUBJECT.matcher(normalized).find() || PLAYBACK_ACTION.matcher(normalized).find();
    }

    static boolean wantsPlayback(String message) {
        return StringUtils.hasText(message) && PLAYBACK_ACTION.matcher(message).find();
    }

    static String failureAnswer(String toolResult) {
        if (!StringUtils.hasText(toolResult)) {
            return "音乐搜索暂时不可用，请稍后再试。";
        }
        String result = toolResult.strip();
        String failedPrefix = "Music catalog request failed: ";
        if (result.startsWith(failedPrefix)) {
            return "音乐搜索失败：" + result.substring(failedPrefix.length());
        }
        if (result.startsWith("Music catalog request failed temporarily")) {
            return "音乐搜索暂时不可用，请稍后再试。";
        }
        return result;
    }
}
