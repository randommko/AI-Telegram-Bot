package org.example.Admin;

import org.example.AiChat.AuthorFormat;
import org.example.AiChat.MessageAuthor;

/**
 * Автор изменения в админке. Ник и имя читаются из {@code tg_users} в момент действия,
 * а логин дублируется текстом, чтобы журнал пережил удаление учётки.
 */
public record Actor(Long tgUserId, String login, String nick, String name) {

    public static Actor owner(Long tgUserId, String nick, String name) {
        return new Actor(tgUserId, AdminSecurityConfig.ADMIN_USERNAME, nick, name);
    }

    /** Подпись для сообщения в чат: {@code @ник (Имя)}; если данных нет — логин. */
    public String sign() {
        String sign = AuthorFormat.sign(new MessageAuthor(tgUserId, nick, name));
        return sign.isEmpty() ? login : sign;
    }
}
