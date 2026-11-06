package org.example.core.aichat;

import java.time.Duration;
import java.time.Instant;

/**
 * Действующая сила обиды: в базе лежит значение на момент updated_at, при чтении
 * оно линейно падает со временем и не уходит ниже нуля.
 */
public final class GrudgeStrength {
    static final int MAX_STRENGTH = 10;

    private GrudgeStrength() {
    }

    public static int effective(int strength, Instant updatedAt, Instant now, int decayPerHour) {
        if (updatedAt == null || decayPerHour <= 0)
            return Math.max(0, Math.min(MAX_STRENGTH, strength));

        double hours = Math.max(0, Duration.between(updatedAt, now).toMinutes() / 60.0);
        int moved = (int) Math.floor(decayPerHour * hours);
        return Math.max(0, strength - moved);
    }
}
