package org.example.admin;

import org.example.core.aichat.Emotion;
import org.example.core.aichat.EmotionRepository;
import org.example.core.aichat.PersonaDetails;
import org.example.core.aichat.PersonaRepository;
import org.example.core.Command;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Проверка карточки персоны. Глобальная уникальность алиаса обеспечивается здесь, а не в схеме:
 * индекс 004 разрешает одинаковые алиасы у персон в разных чатах, и менять схему ради формы не нужно.
 * Поля эмоций строятся по справочнику: код, подпись и диапазон берутся из базы.
 */
@Component
public class AdminPersonaValidator {
    private static final String ALIAS_PATTERN = "[\\p{L}\\p{N}_]+";
    static final String EMOTION_FIELD_PREFIX = "emotion.";

    /** Результат проверки: либо ошибки по полям, либо готовая карточка. */
    public record Result(PersonaDetails details, Map<String, String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private final PersonaRepository personas;
    private final EmotionRepository emotions;

    public AdminPersonaValidator(PersonaRepository personas, EmotionRepository emotions) {
        this.personas = personas;
        this.emotions = emotions;
    }

    public Result validate(PersonaForm form) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = trimmed(form.name());
        if (name.isEmpty())
            errors.put("name", "Имя не может быть пустым");
        else if (personas.nameTaken(name, form.id()))
            errors.put("name", "Персона с таким именем уже есть");

        String prompt = trimmed(form.prompt());
        if (prompt.isEmpty())
            errors.put("prompt", "Промпт не может быть пустым");

        List<String> aliases = parseAliases(form.aliases());
        checkAliases(aliases, errors);

        Map<String, Integer> emotionValues = emotionBaseValues(form, errors);
        int threshold = number(form.aggressionThreshold(), "aggressionThreshold", 1, 10,
                "Порог срыва", errors);
        int hysteriaReplies = number(form.hysteriaReplies(), "hysteriaReplies", 3, 5,
                "Ответов при срыве", errors);
        int hysteriaCooldown = number(form.hysteriaCooldownHours(), "hysteriaCooldownHours", 0, 168,
                "Пауза срыва", errors);
        int maxReplies = number(form.maxReplies(), "maxReplies", 1, 6, "Максимум ответов", errors);
        int replyDelay = number(form.replyDelayMs(), "replyDelayMs", 500, 5000,
                "Задержка ответа", errors);
        int initiativeCooldown = number(form.initiativeCooldownMessages(), "initiativeCooldownMessages", 1, 1000,
                "Пауза инициативы", errors);

        if (errors.isEmpty()) {
            PersonaDetails details = new PersonaDetails(form.id(), name, prompt, aliases,
                    emotionValues, threshold, hysteriaReplies, hysteriaCooldown,
                    maxReplies, replyDelay, Boolean.TRUE.equals(form.initiativeEnabled()), initiativeCooldown);
            return new Result(details, errors);
        }
        return new Result(null, errors);
    }

    /** Значения эмоций проверяются по справочнику: подпись и диапазон берутся из него. */
    private Map<String, Integer> emotionBaseValues(PersonaForm form, Map<String, String> errors) {
        Map<String, String> raw = form.emotionValues() == null ? Map.of() : form.emotionValues();
        Map<String, Integer> values = new LinkedHashMap<>();

        for (Emotion emotion : emotions.findCatalog()) {
            if (emotion.derived())
                continue;
            int value = number(raw.get(emotion.code()), EMOTION_FIELD_PREFIX + emotion.code(),
                    0, 10, emotion.title(), errors);
            values.put(emotion.code(), value);
        }
        return values;
    }

    /** Алиасы из textarea: по одному в строке или через запятую, без повторов в разном регистре. */
    public List<String> parseAliases(String raw) {
        List<String> aliases = new ArrayList<>();
        if (raw == null)
            return aliases;

        Set<String> seen = new HashSet<>();
        for (String part : raw.split("[,\\r\\n]+")) {
            String alias = part.trim();
            if (!alias.isEmpty() && seen.add(alias.toLowerCase()))
                aliases.add(alias);
        }
        return aliases;
    }

    /** Алиас — одно слово и не команда бота; совпадать с алиасом другой персоны он вправе. */
    private void checkAliases(List<String> aliases, Map<String, String> errors) {
        if (aliases.isEmpty()) {
            errors.put("aliases", "Нужен хотя бы один алиас");
            return;
        }

        Set<String> forbidden = forbiddenCommands();
        for (String alias : aliases) {
            String lower = alias.toLowerCase();
            if (!alias.matches(ALIAS_PATTERN)) {
                errors.put("aliases", "Алиас «" + alias + "» должен быть одним словом без пробелов и знаков");
                return;
            }
            if (forbidden.contains(lower)) {
                errors.put("aliases", "Алиас «" + alias + "» совпадает с командой бота");
                return;
            }
        }
    }

    /** Команды без слэша: такое имя нельзя занимать алиасом персоны. */
    static Set<String> forbiddenCommands() {
        Set<String> forbidden = new HashSet<>();
        for (Command command : Command.values()) {
            for (String alias : command.aliases())
                forbidden.add(alias.replace("/", "").toLowerCase());
        }
        return forbidden;
    }

    private int number(String raw, String field, int min, int max, String title, Map<String, String> errors) {
        String value = raw == null ? "" : raw.trim();
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                errors.put(field, title + ": допустимо от " + min + " до " + max);
                return min;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.put(field, title + ": нужно целое число от " + min + " до " + max);
            return min;
        }
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
