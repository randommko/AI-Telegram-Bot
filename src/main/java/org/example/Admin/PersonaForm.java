package org.example.Admin;

import org.example.AiChat.PersonaDetails;

/** Поля карточки персоны как строки: так ошибки ввода видны у конкретного поля. */
public record PersonaForm(
        Integer id,
        String name,
        String prompt,
        String aliases,
        String baseJoy,
        String baseAnger,
        String baseOffence,
        String baseFatigue,
        String baseSarcasm,
        String offenceDecayPerHour,
        String angerThreshold,
        String hysteriaReplies,
        String hysteriaCooldownHours,
        String maxReplies,
        String replyDelayMs,
        Boolean initiativeEnabled,
        String initiativeCooldownMessages) {

    /** Пустая карточка со значениями по умолчанию из схемы. */
    public static PersonaForm blank() {
        return new PersonaForm(null, "", "", "",
                "5", "2", "0", "3", "5", "1", "8", "4", "6", "4", "1500", true, "20");
    }

    public static PersonaForm of(PersonaDetails details) {
        return new PersonaForm(
                details.id(),
                details.name(),
                details.prompt(),
                String.join("\n", details.aliases()),
                String.valueOf(details.baseJoy()),
                String.valueOf(details.baseAnger()),
                String.valueOf(details.baseOffence()),
                String.valueOf(details.baseFatigue()),
                String.valueOf(details.baseSarcasm()),
                String.valueOf(details.offenceDecayPerHour()),
                String.valueOf(details.angerThreshold()),
                String.valueOf(details.hysteriaReplies()),
                String.valueOf(details.hysteriaCooldownHours()),
                String.valueOf(details.maxReplies()),
                String.valueOf(details.replyDelayMs()),
                details.initiativeEnabled(),
                String.valueOf(details.initiativeCooldownMessages()));
    }
}
