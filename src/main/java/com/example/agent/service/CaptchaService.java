package com.example.agent.service;

import jakarta.servlet.http.HttpSession;

public interface CaptchaService {
    byte[] create(HttpSession session);

    void verifyAndConsume(HttpSession session, String submitted);
}
