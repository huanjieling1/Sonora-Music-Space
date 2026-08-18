package com.example.agent.agent.profile;

import com.example.agent.agent.contract.UserTasteContext;

public interface MusicProfileNarrator {
    String narrate(UserTasteContext context, String originalRequest);
}
