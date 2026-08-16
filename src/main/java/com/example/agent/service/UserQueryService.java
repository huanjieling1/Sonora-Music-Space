package com.example.agent.service;

import com.example.agent.model.bo.UserProfileBo;

public interface UserQueryService {
    UserProfileBo getActiveUser(Long userId);
}
