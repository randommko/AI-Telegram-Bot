package org.example.core.aichat;

/**
 * Единый вид подписи автора на входе модели: {@code @ник (Имя)}. Один форматтер
 * на историю и на анализ памяти, чтобы вид запроса не расходился между ними.
 */
public final class AuthorFormat {

    private AuthorFormat() {
    }

    /** {@code @ник (Имя)} или {@code @ник}; пустая строка, если автора нет. */
    public static String sign(MessageAuthor author) {
        if (author == null)
            return "";

        String nick = normalize(author.nick());
        String name = author.name() == null ? "" : author.name().trim();
        boolean hasNick = !nick.isEmpty();
        boolean hasName = !name.isEmpty();

        if (!hasNick && !hasName)
            return "";
        if (!hasNick)
            return "@" + name;
        // Имя совпадает с ником — значит, в Telegram имени нет и дублировать его не нужно.
        if (!hasName || name.equalsIgnoreCase(nick.substring(1)))
            return nick;
        return nick + " (" + name + ")";
    }

    /** Текст сообщения с подписью автора; без автора текст остаётся как есть. */
    public static String render(MessageAuthor author, String text) {
        String sign = sign(author);
        if (sign.isEmpty())
            return text;
        return sign + ": " + text;
    }

    /** Ник в виде ровно с одним ведущим {@code @}. */
    static String normalize(String nick) {
        if (nick == null)
            return "";

        String trimmed = nick.trim();
        int start = 0;
        while (start < trimmed.length() && trimmed.charAt(start) == '@')
            start++;

        String bare = trimmed.substring(start).trim();
        return bare.isEmpty() ? "" : "@" + bare;
    }
}
