package com.example.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    /** 邮箱验证码的带盐摘要，不保存六位明文验证码。 */
    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private EmailVerificationCode(String email, String codeHash, LocalDateTime now) {
        this.email = email;
        this.codeHash = codeHash;
        this.createdAt = now;
        this.expiresAt = now.plusMinutes(5);
    }

    public static EmailVerificationCode issue(String email, String codeHash, LocalDateTime now) {
        return EmailVerificationCode.builder()
                .email(email)
                .codeHash(codeHash)
                .now(now)
                .build();
    }

    public boolean isUsable(LocalDateTime now) {
        return consumedAt == null && failedAttempts < 5 && expiresAt.isAfter(now);
    }

    public void recordFailure() { failedAttempts++; }
    public void consume(LocalDateTime now) { consumedAt = now; }

}
