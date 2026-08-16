package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptchaServiceImplTest {
    private final CaptchaServiceImpl service = new CaptchaServiceImpl();

    @Test
    void createsPngAndStoresChallengeInSession() {
        MockHttpSession session = new MockHttpSession();
        byte[] image = service.create(session);

        assertThat(image).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        assertThat(session.getAttribute(CaptchaServiceImpl.SESSION_KEY)).isNotNull();
    }

    @Test
    void expiredCaptchaFailsAndIsConsumed() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CaptchaServiceImpl.SESSION_KEY,
                new CaptchaServiceImpl.CaptchaState("ABCDE", Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.verifyAndConsume(session, "ABCDE"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("失效");
        assertThat(session.getAttribute(CaptchaServiceImpl.SESSION_KEY)).isNull();
    }

    @Test
    void wrongCaptchaFailsAndCannotBeReused() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CaptchaServiceImpl.SESSION_KEY,
                new CaptchaServiceImpl.CaptchaState("ABCDE", Instant.now().plusSeconds(60)));

        assertThatThrownBy(() -> service.verifyAndConsume(session, "XXXXX"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("错误");
        assertThat(session.getAttribute(CaptchaServiceImpl.SESSION_KEY)).isNull();
    }
}
