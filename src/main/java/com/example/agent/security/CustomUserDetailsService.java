package com.example.agent.security;

import com.example.agent.model.entity.AppUser;
import com.example.agent.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public CustomUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        String value = account == null ? "" : account.trim();
        Optional<AppUser> user;
        if (value.contains("@")) {
            user = users.findByEmailAndDeletedFalse(value.toLowerCase(Locale.ROOT));
        } else if (value.matches("^1[3-9]\\d{9}$")) {
            user = users.findByPhoneAndDeletedFalse(value);
        } else {
            user = users.findByUsernameAndDeletedFalse(value);
        }
        return user.map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    }
}
