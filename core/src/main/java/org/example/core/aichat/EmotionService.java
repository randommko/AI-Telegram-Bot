package org.example.core.aichat;

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
    public static final String ANGER_CODE = "гнев";
    /** Предел сдвига одной шкалы за ход: настроение не должно скакать. */
    static final int MAX_SHIFT = 3;
    /** С этой радости агрессия гасится: агрессивным и довольным одновременно быть нельзя. */
    static final int JOY_SUPPRESSION_FROM = 7;
    /** Эмоция попадает в промпт, только если отошла от характера хотя бы на столько. */
    static final int DEVIATION_THRESHOLD = 2;
    /** Сильная эмоция показывается и без отклонения: характер с гневом 9 обязан звучать. */
    static final int STRONG_VALUE = 7;
    static final int MAX_LINES = 4;
    static final String CALM_LINE = "Твоё текущее состояние: ровное, без сильных эмоций.";
    static final String HEADER = PromptDefaults.EMOTIONS_HEADER;

    private final EmotionRepository emotions;

    public EmotionService(EmotionRepository emotions) {
        this.emotions = emotions;
    }

    /** Готовый блок промпта или пустая строка, если состояния нет или оно не читается. */
    public String blockFor(int personaId) {
        return blockFor(personaId, false);
    }

    /** Тот же блок, но без добивки недостающих строк эмоций — для предпросмотра без записи. */
    public String blockFor(int personaId, boolean readOnly) {
        EmotionState state = stateFor(personaId, readOnly);
        return state == null ? "" : blockText(state);
    }

    /** Содержимое блока без заголовка: заголовок раздела берётся из настроек. */
    public String bodyFor(int personaId) {
        return bodyFor(personaId, false);
    }

    /** Применяет сдвиги плана и прибавку за оскорбление к действующим значениям. */
    public Map<String, Integer> applyShifts(int personaId, Map<String, Integer> deltas) {
        return emotions.applyShifts(personaId, deltas, MAX_SHIFT);
    }

    /** Гнев после срыва возвращается к базовому значению. */
    public void resetAngerToBase(int personaId) {
        emotions.resetToBase(personaId, ANGER_CODE);
    }

    /** Новое значение шкалы: сдвиг ограничен, итог клампится в 0..10. */
    static int shiftValue(int effective, int delta, int maxShift) {
        int bounded = Math.max(-maxShift, Math.min(maxShift, delta));
        return Math.max(0, Math.min(10, effective + bounded));
    }

    /** Сдвиги только по известным шкалам: коды вне справочника молча отбрасываются. */
    static Map<String, Integer> shiftAll(Map<String, Integer> effective,
                                         Map<String, Integer> deltas, int maxShift) {
        Map<String, Integer> shifted = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : effective.entrySet()) {
            Integer delta = deltas.get(entry.getKey());
            if (delta != null)
                shifted.put(entry.getKey(), shiftValue(entry.getValue(), delta, maxShift));
        }
        return shifted;
    }

    public String bodyFor(int personaId, boolean readOnly) {
        EmotionState state = stateFor(personaId, readOnly);
        return state == null ? "" : bodyText(state);
    }

    /** Состояние персоны. Ошибка чтения не должна ломать ответ, поэтому здесь null, а не исключение. */
    public EmotionState stateFor(int personaId) {
        return stateFor(personaId, false);
    }

    public EmotionState stateFor(int personaId, boolean readOnly) {
        try {
            List<Emotion> catalog = emotions.findCatalog();
            if (catalog.isEmpty())
                return null;

            Map<String, List<EmotionPhrase>> phrases = emotions.findPhrases();
            List<PersonaEmotion> stored = readOnly
                    ? emotions.findStoredForPersona(personaId)
                    : emotions.findForPersona(personaId);
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

    /**
     * Текст блока: до четырёх сильных или отошедших от характера эмоций плюс
     * уровень агрессии. Агрессия идёт всегда, кроме полностью ровного состояния.
     */
    static String blockText(EmotionState state) {
        String body = bodyText(state);
        return CALM_LINE.equals(body) ? body : HEADER + "\n" + body;
    }

    /** Текст состояния без заголовка: строки эмоций и уровень агрессии. */
    static String bodyText(EmotionState state) {
        List<EmotionState.Line> selected = state.lines().stream()
                .filter(line -> line.deviation() >= DEVIATION_THRESHOLD || line.value() >= STRONG_VALUE)
                .sorted(Comparator.comparingInt(EmotionService::strength).reversed())
                .limit(MAX_LINES)
                .toList();

        if (selected.isEmpty() && state.aggression() == 0)
            return CALM_LINE;

        List<String> lines = new ArrayList<>();
        for (EmotionState.Line line : selected) {
            lines.add("- " + line.title() + " " + line.value() + "/10: " + line.phrase());
        }
        lines.add("- " + state.aggressionTitle() + " " + state.aggression() + "/10: "
                + state.aggressionPhrase());
        return String.join("\n", lines);
    }

    /** Насколько эмоция заметна для промпта: сильное значение важнее мелкого отклонения. */
    private static int strength(EmotionState.Line line) {
        return Math.max(line.deviation(), line.value());
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
