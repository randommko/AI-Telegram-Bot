package org.example.admin;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Вошедший в админку: владелец из окружения или модератор из базы.
 * Telegram id заполнен только у модератора — по нему считаются его чаты и подпись в чате.
 */
public record AdminPrincipal(String login, String passwordHash, String role, Long tgUserId)
        implements UserDetails {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MODERATOR = "MODERATOR";

    public boolean owner() {
        return ROLE_ADMIN.equals(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
