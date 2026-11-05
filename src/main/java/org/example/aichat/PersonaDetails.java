package org.example.aichat;

import java.util.List;
import java.util.Map;

/** Полная карточка персоны для админки: промпт, алиасы и базовые значения эмоций. */
public record PersonaDetails(
        Integer id,
        String name,
        String prompt,
        List<String> aliases,
        Map<String, Integer> emotionBaseValues,
        int aggressionThreshold,
        int hysteriaReplies,
        int hysteriaCooldownHours,
        int maxReplies,
        int replyDelayMs,
        boolean initiativeEnabled,
        int initiativeCooldownMessages) {
}
