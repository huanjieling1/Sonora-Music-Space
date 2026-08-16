package com.example.agent.service.impl;

import com.example.agent.model.entity.EmailVerificationCode;
import com.example.agent.repository.AppUserRepository;
import com.example.agent.repository.EmailVerificationCodeRepository;
import com.example.agent.security.Sha256PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceImplTest {
    @Test
    void recordsFiveWrongAttemptsAndThenLocksCode() {
        var repository = mock(EmailVerificationCodeRepository.class);
        var users = mock(AppUserRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        var encoder = new Sha256PasswordEncoder(10_000);
        var entity = EmailVerificationCode.issue(
                "user@example.com", encoder.encode("123456"), LocalDateTime.now());
        when(repository.findFirstByEmailOrderByCreatedAtDesc("user@example.com")).thenReturn(Optional.of(entity));
        var service = new EmailVerificationServiceImpl(repository, users, encoder,
                provider, "", "", "", "");

        for (int i = 0; i < 5; i++) {
            assertThat(service.verify("USER@example.com", "000000").success()).isFalse();
        }
        var locked = service.verify("user@example.com", "123456");
        assertThat(entity.getFailedAttempts()).isEqualTo(5);
        assertThat(locked.success()).isFalse();
        assertThat(locked.errorMessage()).contains("错误次数过多");
    }

    @Test
    void sendsThroughJavaMailSenderAndStoresOnlyCodeHash() {
        var repository = mock(EmailVerificationCodeRepository.class);
        var users = mock(AppUserRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(mailSender);
        when(repository.findFirstByEmailOrderByCreatedAtDesc("user@example.com")).thenReturn(Optional.empty());
        var encoder = new Sha256PasswordEncoder(10_000);
        var service = new EmailVerificationServiceImpl(repository, users, encoder,
                provider, "smtp.example.com", "sender@example.com", "secret", "sender@example.com");

        service.sendCode("USER@example.com");

        var captor = org.mockito.ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(repository).saveAndFlush(captor.capture());
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        assertThat(captor.getValue().getCodeHash()).startsWith("sha256$10000$");
    }
}
