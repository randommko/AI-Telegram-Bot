package org.example.AiChat;

import java.util.List;

/** Полная карточка персоны для админки: промпт, алиасы и настройки эмоциональности. */
public record PersonaDetails(
        Integer id,
        String name,
        String prompt,
        List<String> aliases,
        int baseJoy,
        int baseAnger,
        int baseOffence,
        int baseFatigue,
        int baseSarcasm,
        int offenceDecayPerHour,
        int angerThreshold,
        int hysteriaReplies,
        int hysteriaCooldownHours,
        int maxReplies,
        int replyDelayMs,
        boolean initiativeEnabled,
        int initiativeCooldownMessages) {
}
