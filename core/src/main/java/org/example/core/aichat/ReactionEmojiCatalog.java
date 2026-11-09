package org.example.core.aichat;

import java.util.List;

/**
 * Набор эмодзи, разрешённых Telegram для реакций. Админка рисует строки из этого
 * списка, поэтому добавить символ, который Telegram отклонит, нельзя.
 * Список сверяется с документацией Bot API при изменении версии Telegram.
 */
public final class ReactionEmojiCatalog {

    public static final List<String> ALLOWED = List.of(
            "👍", "👎", "❤", "🥰", "🔥", "👏", "😁", "🤔", "🤯", "😱",
            "🤬", "😢", "😍", "🤩", "😮", "😡", "💩", "👌", "🕊", "🤡",
            "🥱", "🥴", "😈", "💯", "🤣", "😘", "💔", "🤨", "😎", "🍾",
            "😭", "😴", "😔", "🤓", "😇", "😏", "😌", "🤝", "✍", "🤗",
            "🫡", "🎉", "⚡", "🏆", "💐");

    private ReactionEmojiCatalog() {
    }

    public static boolean isAllowed(String emoji) {
        return emoji != null && ALLOWED.contains(emoji);
    }

    /** Порядок строки в каталоге; неизвестный эмодзи уходит в конец. */
    public static int order(String emoji) {
        int index = ALLOWED.indexOf(emoji);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
