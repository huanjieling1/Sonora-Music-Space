package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.ao.RegisterUserAo;
import com.example.agent.model.bo.UserProfileBo;
import com.example.agent.model.entity.AppUser;
import com.example.agent.repository.AppUserRepository;
import com.example.agent.service.RegistrationService;
import com.example.agent.utils.EmailUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationServiceImpl(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserProfileBo register(RegisterUserAo request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "两次输入的密码不一致");
        }
        String username = request.username().trim();
        String email = EmailUtils.normalize(request.email());
        String phone = request.phone().trim();
        try {
            ensureUnique(username, email, phone);
            AppUser user = AppUser.register(username, email, phone, passwordEncoder.encode(request.password()));
            return toBo(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(HttpStatus.CONFLICT, "用户名、邮箱或手机号已被使用");
        }
    }

    private void ensureUnique(String username, String email, String phone) {
        if (users.existsByUsername(username)) {
            throw new AppException(HttpStatus.CONFLICT, "用户名已被使用");
        }
        if (users.existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "邮箱已被注册");
        }
        if (users.existsByPhone(phone)) {
            throw new AppException(HttpStatus.CONFLICT, "手机号已被注册");
        }
    }

    private static UserProfileBo toBo(AppUser user) {
        return new UserProfileBo(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
