package org.example.AiChat;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Обращение к персоне по алиасу: целое слово в любом месте сообщения.
 * Возвращает исходный текст сообщения — он уже сохранён в истории и уходит
 * модели целиком. Пустая строка означает, что после вычитания имени
 * никакого вопроса не осталось.
 */
@Component
public class AiAddressParser {
    private static final String WORD_START = "(?<![\\p{L}\\p{N}])";
    private static final String WORD_END = "(?![\\p{L}\\p{N}])";
    private static final String SEPARATORS = " \t\r\n,.;:!?…—–-";

    public Optional<String> question(String text, List<String> aliases) {
        if (text == null || aliases == null || aliases.isEmpty())
            return Optional.empty();

        List<String> usable = aliases.stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .toList();
        if (usable.isEmpty())
            return Optional.empty();

        Matcher addressed = addressPattern(usable).matcher(text);
        if (!addressed.find())
            return Optional.empty();

        if (remainder(text, addressed.start(), addressed.end()).isBlank())
            return Optional.of("");
        return Optional.of(text.strip());
    }

    private Pattern addressPattern(List<String> aliases) {
        String alternation = aliases.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?iu)" + WORD_START + "(?:" + alternation + ")" + WORD_END);
    }

    private String remainder(String text, int start, int end) {
        String left = trimTrailingSeparators(text.substring(0, start));
        String right = trimLeadingSeparators(text.substring(end));
        if (left.isEmpty())
            return right;
        if (right.isEmpty())
            return left;
        return left + " " + right;
    }

    private String trimTrailingSeparators(String value) {
        int end = value.length();
        while (end > 0 && SEPARATORS.indexOf(value.charAt(end - 1)) >= 0)
            end--;
        return value.substring(0, end);
    }

    private String trimLeadingSeparators(String value) {
        int start = 0;
        while (start < value.length() && SEPARATORS.indexOf(value.charAt(start)) >= 0)
            start++;
        return value.substring(start);
    }
}
