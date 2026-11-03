package org.example;

import java.util.ArrayList;
import java.util.List;

/**
 * Режет ответ на части, которые влезают в одно сообщение Telegram.
 * Сначала пробует границы абзаца и строки, потом конец предложения, потом пробел,
 * и только если ничего нет — режет по лимиту.
 */
public final class TextSplitter {
    /** Запас 96 символов до жёсткого лимита Telegram в 4096. */
    public static final int MAX_MESSAGE_LENGTH = 4000;

    private TextSplitter() {
    }

    public static List<String> split(String text, int limit) {
        if (text == null || text.isBlank())
            return List.of();
        if (limit < 1)
            throw new IllegalArgumentException("Лимит части должен быть положительным: " + limit);

        List<String> parts = new ArrayList<>();
        String rest = text.strip();

        while (rest.length() > limit) {
            int cut = Math.min(cutAt(rest, limit), limit);
            String part = rest.substring(0, cut).strip();
            if (!part.isEmpty())
                parts.add(part);
            rest = rest.substring(cut).stripLeading();
        }

        if (!rest.isEmpty())
            parts.add(rest);
        return parts;
    }

    private static int cutAt(String text, int limit) {
        // Слишком короткая первая часть хуже жёсткого разрыва: граница должна быть не раньше половины лимита.
        int min = limit / 2;

        int paragraph = text.lastIndexOf("\n\n", limit - 1);
        if (paragraph >= min)
            return paragraph;

        int line = text.lastIndexOf('\n', limit - 1);
        if (line >= min)
            return line;

        for (String boundary : List.of(". ", "! ", "? ")) {
            int index = text.lastIndexOf(boundary, limit - 2);
            if (index >= min)
                return index + 1;
        }

        int space = text.lastIndexOf(' ', limit - 1);
        if (space >= min)
            return space;

        return limit;
    }
}
