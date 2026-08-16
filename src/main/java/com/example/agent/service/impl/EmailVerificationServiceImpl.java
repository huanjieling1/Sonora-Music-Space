package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.VerificationResultBo;
import com.example.agent.model.entity.EmailVerificationCode;
import com.example.agent.repository.AppUserRepository;
import com.example.agent.repository.EmailVerificationCodeRepository;
import com.example.agent.service.EmailVerificationService;
import com.example.agent.utils.EmailUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private final EmailVerificationCodeRepository codes;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SecureRandom random = new SecureRandom();
    private final String smtpHost;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String from;

    public EmailVerificationServiceImpl(EmailVerificationCodeRepository codes, AppUserRepository users,
                                        PasswordEncoder passwordEncoder,
                                        ObjectProvider<JavaMailSender> mailSenderProvider,
                                        @Value("${spring.mail.host:}") String smtpHost,
                                        @Value("${spring.mail.username:}") String smtpUsername,
                                        @Value("${spring.mail.password:}") String smtpPassword,
                                        @Value("${app.mail.from:}") String from) {
        this.codes = codes;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
        this.smtpHost = smtpHost;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.from = from;
    }

    @Override
    @Transactional
    public void sendCode(String rawEmail) {
        String email = EmailUtils.normalize(rawEmail);
        ensureMailConfigured();
        if (users.existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "该邮箱已注册");
        }

        LocalDateTime now = LocalDateTime.now();
        codes.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(latest -> {
            if (latest.getCreatedAt().plusSeconds(60).isAfter(now)) {
                throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请 60 秒后再试");
            }
        });

        String plainCode = Integer.toString(100000 + random.nextInt(900000));
        EmailVerificationCode entity = EmailVerificationCode.issue(
                email, passwordEncoder.encode(plainCode), now);
        codes.saveAndFlush(entity);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("LangChain4j Agent 注册验证码");
        message.setText("您的注册验证码是：" + plainCode + "\n\n验证码 5 分钟内有效，请勿转发或告知他人。\n如非本人操作，请忽略此邮件。");
        try {
            mailSenderProvider.getObject().send(message);
        } catch (MailException | org.springframework.beans.BeansException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "邮件发送失败，请检查 SMTP 配置或稍后重试");
        }
    }

    @Override
    @Transactional
    public VerificationResultBo verify(String rawEmail, String submittedCode) {
        String email = EmailUtils.normalize(rawEmail);
        EmailVerificationCode latest = codes.findFirstByEmailOrderByCreatedAtDesc(email).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (latest == null) {
            return VerificationResultBo.failure("请先获取邮箱验证码");
        }
        if (latest.getConsumedAt() != null) {
            return VerificationResultBo.failure("邮箱验证码已使用，请重新获取");
        }
        if (!latest.getExpiresAt().isAfter(now)) {
            return VerificationResultBo.failure("邮箱验证码已过期，请重新获取");
        }
        if (latest.getFailedAttempts() >= 5) {
            return VerificationResultBo.failure("邮箱验证码错误次数过多，请重新获取");
        }
        if (!passwordEncoder.matches(submittedCode, latest.getCodeHash())) {
            latest.recordFailure();
            return VerificationResultBo.failure("邮箱验证码错误，还可尝试 " + Math.max(0, 5 - latest.getFailedAttempts()) + " 次");
        }
        return VerificationResultBo.success(latest.getId());
    }

    private void ensureMailConfigured() {
        if (!StringUtils.hasText(smtpHost) || !StringUtils.hasText(smtpUsername)
                || !StringUtils.hasText(smtpPassword) || !StringUtils.hasText(from)) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SMTP 尚未配置，请先在 .env 中填写发件服务器、账号和授权码");
        }
    }
}
