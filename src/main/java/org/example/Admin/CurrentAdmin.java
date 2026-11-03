package org.example.Admin;

import org.example.Settings.Settings;
import org.example.Settings.SettingsService;
import org.example.Users.UsersRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

/**
 * Кто сейчас работает в админке. Владелец входит паролем из окружения и не имеет строки
 * в tg_users, поэтому его ник и имя берутся из настройки {@code ownerChatId}.
 */
@Component
public class CurrentAdmin {
    private final SettingsService settings;
    private final UsersRepository users;

    public CurrentAdmin(SettingsService settings, UsersRepository users) {
        this.settings = settings;
        this.users = users;
    }

    public Optional<AdminPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated())
            return Optional.empty();

        return authentication.getPrincipal() instanceof AdminPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public boolean isOwner() {
        return principal().map(AdminPrincipal::owner).orElse(false);
    }

    public Optional<Long> moderatorId() {
        return principal().filter(principal -> !principal.owner()).map(AdminPrincipal::tgUserId);
    }

    /** Автор действия с актуальными ником и именем; пустой, если никто не вошёл. */
    public Optional<Actor> actor() {
        Optional<AdminPrincipal> principal = principal();
        if (principal.isEmpty())
            return Optional.empty();

        AdminPrincipal current = principal.get();
        if (!current.owner()) {
            User user = users.getUserByID(current.tgUserId());
            return Optional.of(new Actor(current.tgUserId(), current.login(),
                    user == null ? null : user.getUserName(),
                    user == null ? null : user.getFirstName()));
        }

        Long ownerId = ownerChatId();
        User owner = ownerId == null ? null : users.getUserByID(ownerId);
        return Optional.of(Actor.owner(ownerId,
                owner == null ? null : owner.getUserName(),
                owner == null ? null : owner.getFirstName()));
    }

    /** Telegram id владельца из настройки: тот же получатель, что и у уведомления о запуске. */
    public Long ownerChatId() {
        String configured = settings.getSettingValue(Settings.OWNER_CHAT_ID);
        if (configured == null || configured.isBlank())
            return null;

        try {
            return Long.parseLong(configured.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
