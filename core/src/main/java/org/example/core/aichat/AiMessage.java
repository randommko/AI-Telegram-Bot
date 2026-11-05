package org.example.core.aichat;

/** Сообщение истории: роль, текст без служебного префикса и, у людей, автор. */
public record AiMessage(String role, String content, MessageAuthor author) {

    /** Сообщение без автора: ответ бота или служебная строка. */
    public AiMessage(String role, String content) {
        this(role, content, MessageAuthor.none());
    }
}
