package com.example.agent.agent.profile;

import com.example.agent.agent.contract.UserTasteContext;

public interface MusicProfileContextReader {
    UserTasteContext read(long userId);
}
