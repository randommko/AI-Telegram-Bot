package org.example.AiChat;

import java.time.Duration;
import java.time.Instant;

/** Поведение персоны в ответе: сколько реплик, пауза, порог срыва и его параметры. */
public record PersonaSettings(
        int personaId,
        int maxReplies,
        int replyDelayMs,
        int aggressionThreshold,
        int hysteriaReplies,
        int hysteriaCooldownHours,
        Instant lastHysteriaAt) {

    public boolean hysteriaCooldownPassed(Instant now) {
        if (lastHysteriaAt == null)
            return true;
        return Duration.between(lastHysteriaAt, now).toHours() >= hysteriaCooldownHours;
    }
}
