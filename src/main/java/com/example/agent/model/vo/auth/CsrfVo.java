package com.example.agent.model.vo.auth;

/** Vue 客户端提交非安全请求所需的 CSRF 信息。 */
public record CsrfVo(String token, String headerName) {
}
