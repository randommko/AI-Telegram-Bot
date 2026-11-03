package org.example.Admin;

import org.example.Config.BotProperties;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Два вида принципалов в одной цепочке: владелец обслуживается паролем из окружения,
 * модераторы — учётками из базы. Логин {@code admin} закреплён за владельцем.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final BotProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccountsRepository accounts;

    public AdminUserDetailsService(BotProperties properties, PasswordEncoder passwordEncoder,
                                   AdminAccountsRepository accounts) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (AdminSecurityConfig.ADMIN_USERNAME.equalsIgnoreCase(username)) {
            if (!properties.hasAdmin())
                throw new UsernameNotFoundException("Админка выключена");
            return new AdminPrincipal(AdminSecurityConfig.ADMIN_USERNAME,
                    passwordEncoder.encode(properties.admin().password()),
                    AdminPrincipal.ROLE_ADMIN, null);
        }

        return accounts.findByLogin(username)
                .map(account -> new AdminPrincipal(account.login(), account.passwordHash(),
                        AdminPrincipal.ROLE_MODERATOR, account.tgUserId()))
                .orElseThrow(() -> new UsernameNotFoundException("Учётка модератора не найдена"));
    }
}
