package org.example.Admin;

import org.example.AiChat.PersonaDetails;
import org.example.AiChat.PersonaRepository;
import org.example.CommandDispatcher.Command;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Проверка карточки персоны. Глобальная уникальность алиаса обеспечивается здесь, а не в схеме:
 * индекс 004 разрешает одинаковые алиасы у персон в разных чатах, и менять схему ради формы не нужно.
 */
@Component
public class AdminPersonaValidator {
    /** Имена-обращения команды AI: алиас персоны вправе с ними совпадать. */
    static final Set<String> PERSONA_ADDRESS_NAMES = Set.of("юра", "юрий", "юрец");
    private static final String ALIAS_PATTERN = "[\\p{L}\\p{N}_]+";

    /** Результат проверки: либо ошибки по полям, либо готовая карточка. */
    public record Result(PersonaDetails details, Map<String, String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private final PersonaRepository personas;

    public AdminPersonaValidator(PersonaRepository personas) {
        this.personas = personas;
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
        checkAliases(aliases, form.id(), errors);

        int joy = number(form.baseJoy(), "baseJoy", 0, 10, "Радость", errors);
        int anger = number(form.baseAnger(), "baseAnger", 0, 10, "Злость", errors);
        int offence = number(form.baseOffence(), "baseOffence", 0, 10, "Обида", errors);
        int fatigue = number(form.baseFatigue(), "baseFatigue", 0, 10, "Усталость", errors);
        int sarcasm = number(form.baseSarcasm(), "baseSarcasm", 0, 10, "Сарказм", errors);
        int decay = number(form.offenceDecayPerHour(), "offenceDecayPerHour", 0, 10,
                "Затухание обиды", errors);
        int threshold = number(form.angerThreshold(), "angerThreshold", 1, 10,
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
                    joy, anger, offence, fatigue, sarcasm, decay, threshold,
                    hysteriaReplies, hysteriaCooldown, maxReplies, replyDelay,
                    Boolean.TRUE.equals(form.initiativeEnabled()), initiativeCooldown);
            return new Result(details, errors);
        }
        return new Result(null, errors);
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

    private void checkAliases(List<String> aliases, Integer personaId, Map<String, String> errors) {
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
            if (personas.aliasOwner(alias, personaId).isPresent()) {
                errors.put("aliases", "Алиас «" + alias + "» уже занят другой персоной");
                return;
            }
        }
    }

    /** Команды без слэша, кроме имён-обращений AI: по ним и так вызывается персона. */
    static Set<String> forbiddenCommands() {
        Set<String> forbidden = new HashSet<>();
        for (Command command : Command.values()) {
            for (String alias : command.aliases()) {
                String clean = alias.replace("/", "").toLowerCase();
                if (command == Command.AI && PERSONA_ADDRESS_NAMES.contains(clean))
                    continue;
                forbidden.add(clean);
            }
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
