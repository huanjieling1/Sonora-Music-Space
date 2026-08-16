package com.example.agent.model.bo;

/** 邮箱验证码校验结果，为后续启用邮件注册流程保留。 */
public record VerificationResultBo(boolean success, Long codeId, String errorMessage) {
    public static VerificationResultBo success(Long codeId) {
        return new VerificationResultBo(true, codeId, null);
    }

    public static VerificationResultBo failure(String message) {
        return new VerificationResultBo(false, null, message);
    }
}
