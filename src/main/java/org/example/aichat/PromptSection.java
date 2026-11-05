package org.example.aichat;

import java.util.List;

import static org.example.settings.Settings.AI_PROMPT_ANSWER_FORMAT;
import static org.example.settings.Settings.AI_PROMPT_EMOTIONS_HEADER;
import static org.example.settings.Settings.AI_PROMPT_GRUDGES_HEADER;
import static org.example.settings.Settings.AI_PROMPT_MEMORY_CORE_HEADER;
import static org.example.settings.Settings.AI_PROMPT_MEMORY_REGULAR_HEADER;
import static org.example.settings.Settings.AI_PROMPT_PEOPLE_HEADER;
import static org.example.settings.Settings.AI_PROMPT_TECHNICAL;
import static org.example.settings.Settings.AI_PROMPT_TIME_TEMPLATE;
import static org.example.settings.Settings.AI_PROMPT_TIME_ZONE;

/**
 * Реестр разделов промпта в порядке сборки. Порядок, заголовки и бюджеты живут
 * в коде: настройкой их поменять нельзя, иначе приоритет разделов станет случайным.
 */
public enum PromptSection {
    TECHNICAL("Техника", null, 800, List.of(
            field(AI_PROMPT_TECHNICAL, "Текст технической части", PromptDefaults.TECHNICAL, true))),
    PERSONA("Персона", "## Кто ты", 6000, List.of()),
    EMOTIONS("Эмоции", "## Твоё состояние сейчас", 800, List.of(
            field(AI_PROMPT_EMOTIONS_HEADER, "Заголовок раздела", PromptDefaults.EMOTIONS_HEADER, true))),
    GRUDGES("Обиды", "## Обиды", 600, List.of(
            field(AI_PROMPT_GRUDGES_HEADER, "Заголовок раздела", PromptDefaults.GRUDGES_HEADER, true))),
    MEMORY("Память", "## Что ты помнишь", 4000, List.of(
            field(AI_PROMPT_MEMORY_CORE_HEADER, "Заголовок незыблемых", PromptDefaults.MEMORY_CORE_HEADER, true),
            field(AI_PROMPT_MEMORY_REGULAR_HEADER, "Заголовок обычных", PromptDefaults.MEMORY_REGULAR_HEADER, true))),
    PEOPLE("Люди", "## Кто в чате", 800, List.of(
            field(AI_PROMPT_PEOPLE_HEADER, "Заголовок раздела", PromptDefaults.PEOPLE_HEADER, true))),
    TIME("Время", "## Сейчас", 200, List.of(
            field(AI_PROMPT_TIME_TEMPLATE, "Строка времени", PromptDefaults.TIME_TEMPLATE, true),
            field(AI_PROMPT_TIME_ZONE, "Часовой пояс", PromptDefaults.TIME_ZONE, false))),
    ANSWER_FORMAT("Формат ответа", "## Как отвечать", 300, List.of(
            field(AI_PROMPT_ANSWER_FORMAT, "Текст раздела", PromptDefaults.ANSWER_FORMAT, true)));

    /** Редактируемое поле раздела: ключ настройки, подпись, текст по умолчанию. */
    public record Template(String key, String label, String defaultText, boolean multiline) {
    }

    private final String title;
    private final String heading;
    private final int budget;
    private final List<Template> templates;

    PromptSection(String title, String heading, int budget, List<Template> templates) {
        this.title = title;
        this.heading = heading;
        this.budget = budget;
        this.templates = templates;
    }

    public String title() {
        return title;
    }

    /** Заголовок в промпте; null у технической части — она идёт без заголовка. */
    public String heading() {
        return heading;
    }

    public int budget() {
        return budget;
    }

    public List<Template> templates() {
        return templates;
    }

    private static Template field(String key, String label, String defaultText, boolean multiline) {
        return new Template(key, label, defaultText, multiline);
    }
}
