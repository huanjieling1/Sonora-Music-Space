package com.example.agent.service;

import com.example.agent.model.bo.VerificationResultBo;

public interface EmailVerificationService {
    void sendCode(String rawEmail);

    VerificationResultBo verify(String rawEmail, String submittedCode);
}
