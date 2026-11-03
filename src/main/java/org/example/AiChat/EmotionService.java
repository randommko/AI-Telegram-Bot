package org.example.AiChat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Действующее состояние персоны: затухание эмоций к характеру, вычисляемая
 * агрессия и текст блока для системного промпта. Всё считается при чтении,
 * поэтому планировщика нет, а в базе лежит значение на момент updated_at.
 */
@Component
public class EmotionService {
    private static final Logger logger = LoggerFactory.getLogger(EmotionService.class);

    static final String JOY_CODE = "радость";
    static final String AGGRESSION_CODE = "агрессия";
    /** С этой радости агрессия гасится: агрессивным и довольным одновременно быть нельзя. */
    static final int JOY_SUPPRESSION_FROM = 7;
    /** Эмоция попадает в промпт, только если отошла от характера хотя бы на столько. */
    static final int DEVIATION_THRESHOLD = 2;
    static final int MAX_LINES = 4;
    static final String CALM_LINE = "Твоё текущее состояние: ровное, без сильных эмоций.";
    static final String HEADER = "Твоё текущее состояние сейчас (это настроение момента, а не характер):";

    private final EmotionRepository emotions;

    public EmotionService(EmotionRepository emotions) {
        this.emotions = emotions;
    }

    /** Готовый блок промпта или пустая строка, если состояния нет или оно не читается. */
    public String blockFor(int personaId) {
        EmotionState state = stateFor(personaId);
        return state == null ? "" : blockText(state);
    }

    /** Состояние персоны. Ошибка чтения не должна ломать ответ, поэтому здесь null, а не исключение. */
    public EmotionState stateFor(int personaId) {
        try {
            List<Emotion> catalog = emotions.findCatalog();
            if (catalog.isEmpty())
                return null;

            Map<String, List<EmotionPhrase>> phrases = emotions.findPhrases();
            List<PersonaEmotion> stored = emotions.findForPersona(personaId);
            Instant now = Instant.now();

            Map<String, Integer> effective = new LinkedHashMap<>();
            List<EmotionState.Line> lines = new ArrayList<>();
            for (PersonaEmotion personaEmotion : stored) {
                Emotion emotion = byCode(catalog, personaEmotion.emotionCode());
                if (emotion == null || emotion.derived())
                    continue;

                int value = effectiveValue(personaEmotion.baseValue(), personaEmotion.value(),
                        emotion.decayPerHour(), Duration.between(personaEmotion.updatedAt(), now));
                effective.put(emotion.code(), value);
                lines.add(new EmotionState.Line(
                        emotion.code(),
                        emotion.title(),
                        personaEmotion.baseValue(),
                        value,
                        Math.abs(value - personaEmotion.baseValue()),
                        phraseFor(phrases.get(emotion.code()), value)));
            }

            Emotion aggression = byCode(catalog, AGGRESSION_CODE);
            int level = aggression(effective, catalog);
            return new EmotionState(
                    lines,
                    level,
                    aggression == null ? "Агрессия" : aggression.title(),
                    phraseFor(phrases.get(AGGRESSION_CODE), level));
        } catch (Exception e) {
            logger.error("Ошибка сборки состояния персоны {}: {}", personaId, String.valueOf(e));
            return null;
        }
    }

    /**
     * Действующее значение эмоции: линейно движется к базе со скоростью затухания
     * и не проскакивает её. База выше текущего — значение подтягивается вверх.
     */
    static int effectiveValue(int base, int stored, double decayPerHour, Duration elapsed) {
        double hours = Math.max(0, elapsed.toMinutes() / 60.0);
        double moved = decayPerHour * hours;

        if (stored > base)
            return clamp((int) Math.max(base, Math.floor(stored - moved)));
        if (stored < base)
            return clamp((int) Math.min(base, Math.ceil(stored + moved)));
        return clamp(stored);
    }

    /**
     * Уровень агрессии: взвешенная сумма значений эмоций. Округление вниз, чтобы
     * спокойная персона с положительными базовыми значениями получала 0.
     */
    static int aggression(Map<String, Integer> values, List<Emotion> catalog) {
        double sum = 0;
        for (Emotion emotion : catalog) {
            Integer value = values.get(emotion.code());
            if (!emotion.derived() && value != null)
                sum += emotion.aggressionWeight() * value;
        }

        // Веса дробные, поэтому сумма ровно 10 может прийти как 9.9999999: сдвигаем на эпсилон.
        int level = (int) Math.floor(Math.max(0, Math.min(10, sum)) + 1e-9);
        Integer joy = values.get(JOY_CODE);
        if (joy != null && joy >= JOY_SUPPRESSION_FROM)
            level = Math.min(level, 10 - joy);
        return level;
    }

    /** Фраза полосы, в которую попадает значение; пустая строка, если полосы нет. */
    static String phraseFor(List<EmotionPhrase> phrases, int value) {
        if (phrases == null)
            return "";
        for (EmotionPhrase phrase : phrases) {
            if (phrase.covers(value))
                return phrase.phrase();
        }
        return "";
    }

    /** Текст блока: до четырёх эмоций, отошедших от характера, плюс уровень агрессии. */
    static String blockText(EmotionState state) {
        List<EmotionState.Line> selected = state.lines().stream()
                .filter(line -> line.deviation() >= DEVIATION_THRESHOLD)
                .sorted(Comparator.comparingInt(EmotionState.Line::deviation).reversed())
                .limit(MAX_LINES)
                .toList();

        if (selected.isEmpty())
            return CALM_LINE;

        StringBuilder block = new StringBuilder(HEADER);
        for (EmotionState.Line line : selected) {
            block.append('\n')
                    .append("- ").append(line.title()).append(' ').append(line.value()).append("/10: ")
                    .append(line.phrase());
        }
        block.append('\n')
                .append("- ").append(state.aggressionTitle()).append(' ').append(state.aggression()).append("/10: ")
                .append(state.aggressionPhrase());
        return block.toString();
    }

    private static Emotion byCode(List<Emotion> catalog, String code) {
        for (Emotion emotion : catalog) {
            if (emotion.code().equals(code))
                return emotion;
        }
        return null;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(10, value));
    }
}
