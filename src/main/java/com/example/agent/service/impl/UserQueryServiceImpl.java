package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.UserProfileBo;
import com.example.agent.model.entity.AppUser;
import com.example.agent.repository.AppUserRepository;
import com.example.agent.service.UserQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserQueryServiceImpl implements UserQueryService {
    private final AppUserRepository users;

    public UserQueryServiceImpl(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileBo getActiveUser(Long userId) {
        AppUser user = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return new UserProfileBo(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
