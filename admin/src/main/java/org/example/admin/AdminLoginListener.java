package org.example.admin;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Отметка последнего входа модератора — по ней видно, пользовались ли учёткой. */
@Component
public class AdminLoginListener {
    private final AdminAccountsRepository accounts;

    public AdminLoginListener(AdminAccountsRepository accounts) {
        this.accounts = accounts;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof AdminPrincipal principal) || principal.owner())
            return;

        accounts.markLogin(principal.login());
    }
}
