package org.example.admin;

import org.example.core.aichat.Emotion;
import org.example.core.aichat.PersonaDetails;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Поля карточки персоны как строки: так ошибки ввода видны у конкретного поля.
 * Значения эмоций идут картой «код эмоции → значение», набор кодов берётся из справочника.
 */
public record PersonaForm(
        Integer id,
        String name,
        String prompt,
        String aliases,
        Map<String, String> emotionValues,
        String aggressionThreshold,
        String hysteriaReplies,
        String hysteriaCooldownHours,
        String maxReplies,
        String replyDelayMs,
        Boolean initiativeEnabled,
        String initiativeCooldownMessages) {

    /** Пустая карточка со значениями по умолчанию из справочника. */
    public static PersonaForm blank(List<Emotion> catalog) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Emotion emotion : catalog) {
            if (!emotion.derived())
                values.put(emotion.code(), String.valueOf(emotion.baseDefault()));
        }
        return new PersonaForm(null, "", "", "", values, "8", "4", "6", "4", "1500", true, "20");
    }

    public static PersonaForm of(PersonaDetails details, List<Emotion> catalog) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Emotion emotion : catalog) {
            if (emotion.derived())
                continue;
            Integer base = details.emotionBaseValues().get(emotion.code());
            values.put(emotion.code(), String.valueOf(base == null ? emotion.baseDefault() : base));
        }

        return new PersonaForm(
                details.id(),
                details.name(),
                details.prompt(),
                String.join("\n", details.aliases()),
                values,
                String.valueOf(details.aggressionThreshold()),
                String.valueOf(details.hysteriaReplies()),
                String.valueOf(details.hysteriaCooldownHours()),
                String.valueOf(details.maxReplies()),
                String.valueOf(details.replyDelayMs()),
                details.initiativeEnabled(),
                String.valueOf(details.initiativeCooldownMessages()));
    }
}
