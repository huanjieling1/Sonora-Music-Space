package com.example.agent.service;

import com.example.agent.model.ao.RegisterUserAo;
import com.example.agent.model.bo.UserProfileBo;

public interface RegistrationService {
    UserProfileBo register(RegisterUserAo request);
}
