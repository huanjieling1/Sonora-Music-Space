package com.example.agent.service.impl;

import com.example.agent.agent.intent.MusicIntentAgent;

/** Deterministic recovery for explicit music requests missed by model tool routing. */
final class MusicRequestFallback {
    private MusicRequestFallback() {
    }

    static boolean shouldPlayRandomQqPublicPlaylist(String message) {
        return MusicIntentAgent.shouldPlayRandomQqPublicPlaylist(message);
    }

    static boolean shouldSearchQqPlaylists(String message) {
        return MusicIntentAgent.shouldSearchQqPlaylists(message);
    }

    static boolean shouldSearchQqArtists(String message) {
        return MusicIntentAgent.shouldSearchQqArtists(message);
    }

    static boolean shouldSearch(String message) {
        return MusicIntentAgent.shouldSearch(message);
    }

    static boolean wantsPlayback(String message) {
        return MusicIntentAgent.wantsPlayback(message);
    }

    static String failureAnswer(String toolResult) {
        return MusicIntentAgent.failureAnswer(toolResult);
    }
}
