package com.example.agent.security;

import com.example.agent.model.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AppUserPrincipal(Long id, String username, String email, String phone, String password)
        implements UserDetails {

    public static AppUserPrincipal from(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(), user.getPassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }
}
