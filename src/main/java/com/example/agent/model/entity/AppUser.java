package com.example.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    /** 用户主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户名，允许中文、字母、数字和下划线。 */
    @Column(nullable = false, length = 32, unique = true)
    private String username;

    /** 标准化为小写的用户邮箱。 */
    @Column(nullable = false, length = 254, unique = true)
    private String email;

    /** 中国大陆手机号码。 */
    @Column(nullable = false, length = 20, unique = true)
    private String phone;

    /** 带随机盐并迭代计算的 SHA-256 摘要，永不保存明文密码。 */
    @Column(nullable = false, length = 255)
    private String password;

    /** 用户记录创建日期。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 用户记录最后修改日期。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 逻辑删除状态，false 表示正常，true 表示已删除。 */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder(access = AccessLevel.PRIVATE)
    private AppUser(String username, String email, String phone, String password) {
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.deleted = false;
    }

    public static AppUser register(String username, String email, String phone, String password) {
        return AppUser.builder()
                .username(username)
                .email(email)
                .phone(phone)
                .password(password)
                .build();
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markDeleted() {
        this.deleted = true;
    }
}
