package org.example.AiChat;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiAddressParser {
    // Обращение — отдельное слово в начале сообщения: после него идёт разделитель или конец строки.
    private static final Pattern ADDRESS = Pattern.compile("^\\s*(?iu:юра|юрец|юрчик|юрий|юр)(?:\\s|[,.;:!?…—–-]|$)");
    private static final Pattern LEADING_SEPARATORS = Pattern.compile("^[\\s,.;:!?…—–-]+");

    public Optional<String> question(String text) {
        if (text == null)
            return Optional.empty();

        Matcher addressed = ADDRESS.matcher(text);
        if (!addressed.find())
            return Optional.empty();

        String question = LEADING_SEPARATORS.matcher(text.substring(addressed.end())).replaceFirst("");
        return Optional.of(question.strip());
    }
}
