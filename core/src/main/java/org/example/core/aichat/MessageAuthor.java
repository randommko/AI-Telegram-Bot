package org.example.core.aichat;

import org.telegram.telegrambots.meta.api.objects.User;

/**
 * Автор сообщения: Telegram ID, ник и имя. Ник хранится с ведущим {@code @},
 * как в tg_users, поэтому его не нужно склеивать при чтении.
 */
public record MessageAuthor(Long userId, String nick, String name) {
    private static final MessageAuthor NONE = new MessageAuthor(null, null, null);

    public static MessageAuthor none() {
        return NONE;
    }

    /** Автор из сообщения Telegram; ник приводится к виду с {@code @}. */
    public static MessageAuthor from(User user) {
        if (user == null)
            return NONE;

        String nick = user.getUserName();
        if (nick != null && !nick.startsWith("@"))
            nick = "@" + nick;
        return new MessageAuthor(user.getId(), nick, user.getFirstName());
    }

    public boolean isEmpty() {
        return userId == null && isBlank(nick) && isBlank(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
