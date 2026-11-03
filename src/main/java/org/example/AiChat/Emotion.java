package org.example.AiChat;

/**
 * Шкала эмоции из справочника. isDerived = true только у агрессии: её значение
 * вычисляется из остальных, поэтому строки в persona_emotions у неё нет.
 */
public record Emotion(
        String code,
        String title,
        String description,
        String polarity,
        double aggressionWeight,
        double decayPerHour,
        int baseDefault,
        boolean derived,
        int sortOrder) {
}
