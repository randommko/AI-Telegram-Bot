package org.example.AiChat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmotionServiceTest {

    private static final List<Emotion> CATALOG = List.of(
            new Emotion("радость", "Радость", "", "плюс", -0.25, 1.00, 5, false, 10),
            new Emotion("печаль", "Печаль", "", "минус", 0, 0.50, 2, false, 20),
            new Emotion("гнев", "Гнев", "", "минус", 0.55, 1.50, 2, false, 30),
            new Emotion("страх", "Страх", "", "минус", 0, 1.00, 2, false, 40),
            new Emotion("брезгливость", "Брезгливость", "", "минус", 0.20, 1.00, 1, false, 50),
            new Emotion("тревожность", "Тревожность", "", "минус", 0, 0.50, 3, false, 60),
            new Emotion("зависть", "Зависть", "", "минус", 0.10, 0.50, 1, false, 70),
            new Emotion("стыд", "Стыд", "", "минус", 0, 0.50, 1, false, 80),
            new Emotion("хандра", "Хандра", "", "минус", 0, 0.50, 3, false, 90),
            new Emotion("ностальгия", "Ностальгия", "", "плюс", -0.10, 0.20, 2, false, 100),
            new Emotion("сарказм", "Сарказм", "", "стиль", 0.15, 0.50, 5, false, 110),
            new Emotion("агрессия", "Агрессия", "", "стиль", 0, 0, 0, true, 999));

    @Test
    void emotionMovesTowardBaseOverTime() {
        assertEquals(6, EmotionService.effectiveValue(2, 9, 1.5, Duration.ofHours(2)));
    }

    @Test
    void raisedBasePullsValueUp() {
        assertEquals(3, EmotionService.effectiveValue(9, 2, 1.0, Duration.ofHours(1)));
    }

    @Test
    void decayNeverGoesBelowBase() {
        assertEquals(2, EmotionService.effectiveValue(2, 9, 1.5, Duration.ofHours(24)));
    }

    @Test
    void allAggressiveEmotionsAtMaxGiveTen() {
        Map<String, Integer> values = baseValues();
        values.put("радость", 0);
        values.put("ностальгия", 0);
        values.put("гнев", 10);
        values.put("брезгливость", 10);
        values.put("зависть", 10);
        values.put("сарказм", 10);

        assertEquals(10, EmotionService.aggression(values, CATALOG));
    }

    @Test
    void calmPersonaOnItsBaseHasNoAggression() {
        assertEquals(0, EmotionService.aggression(baseValues(), CATALOG));
    }

    @Test
    void maximumJoySuppressesAggressionCompletely() {
        Map<String, Integer> values = baseValues();
        values.put("радость", 10);
        values.put("гнев", 10);

        assertEquals(0, EmotionService.aggression(values, CATALOG));
    }

    @Test
    void joyAboveThresholdCapsAggression() {
        Map<String, Integer> values = baseValues();
        values.put("радость", 7);
        values.put("гнев", 10);

        assertTrue(EmotionService.aggression(values, CATALOG) <= 3);
    }

    @Test
    void phraseIsChosenByBand() {
        List<EmotionPhrase> phrases = List.of(
                new EmotionPhrase("гнев", 0, 1, "a"),
                new EmotionPhrase("гнев", 2, 4, "b"),
                new EmotionPhrase("гнев", 5, 6, "c"),
                new EmotionPhrase("гнев", 7, 8, "d"),
                new EmotionPhrase("гнев", 9, 10, "e"));

        assertEquals("b", EmotionService.phraseFor(phrases, 4));
        assertEquals("c", EmotionService.phraseFor(phrases, 5));
        assertEquals("e", EmotionService.phraseFor(phrases, 10));
        assertEquals("", EmotionService.phraseFor(null, 10));
    }

    @Test
    void angryPersonaGetsAngerAndAggressionLines() {
        EmotionState state = new EmotionState(
                List.of(new EmotionState.Line("гнев", "Гнев", 2, 8, 6, "груби")),
                7, "Агрессия", "дави");

        String block = EmotionService.blockText(state);

        assertTrue(block.startsWith(EmotionService.HEADER), block);
        assertTrue(block.contains("- Гнев 8/10: груби"), block);
        assertTrue(block.contains("- Агрессия 7/10: дави"), block);
        assertFalse(block.contains(EmotionService.CALM_LINE), block);
    }

    @Test
    void calmPersonaGetsOneLine() {
        EmotionState state = new EmotionState(
                List.of(new EmotionState.Line("гнев", "Гнев", 2, 3, 1, "суховато")),
                0, "Агрессия", "спокойно");

        assertEquals(EmotionService.CALM_LINE, EmotionService.blockText(state));
    }

    @Test
    void strongEmotionIsShownEvenWhenEqualToBase() {
        EmotionState state = new EmotionState(
                List.of(new EmotionState.Line("гнев", "Гнев", 9, 9, 0, "оскорбляй")),
                4, "Агрессия", "дави");

        String block = EmotionService.blockText(state);

        assertTrue(block.contains("- Гнев 9/10: оскорбляй"), block);
        assertTrue(block.contains("- Агрессия 4/10: дави"), block);
        assertFalse(block.contains(EmotionService.CALM_LINE), block);
    }

    @Test
    void calmMoodKeepsAggressionLineWhenItIsNotZero() {
        EmotionState state = new EmotionState(
                List.of(new EmotionState.Line("гнев", "Гнев", 2, 3, 1, "суховато")),
                3, "Агрессия", "дави");

        String block = EmotionService.blockText(state);

        assertTrue(block.contains("- Агрессия 3/10: дави"), block);
        assertFalse(block.contains(EmotionService.CALM_LINE), block);
    }

    @Test
    void onlyFourStrongestEmotionsAndAggressionAreListed() {
        List<EmotionState.Line> lines = new ArrayList<>();
        for (int deviation = 2; deviation <= 7; deviation++)
            lines.add(new EmotionState.Line("e" + deviation, "Эмоция " + deviation, 0, deviation, deviation, "фраза"));

        String block = EmotionService.blockText(new EmotionState(lines, 5, "Агрессия", "дави"));

        String[] rows = block.split("\n");
        assertEquals(6, rows.length, "заголовок, четыре эмоции и агрессия");
        assertTrue(block.contains("Эмоция 7"), block);
        assertFalse(block.contains("Эмоция 2"), block);
    }

    @Test
    void unreadableEmotionsDoNotBreakTheBlock() {
        EmotionRepository repository = mock(EmotionRepository.class);
        when(repository.findCatalog()).thenThrow(new RuntimeException("нет связи"));

        assertEquals("", new EmotionService(repository).blockFor(1));
    }

    @Test
    void emptyCatalogMeansNoBlock() {
        EmotionRepository repository = mock(EmotionRepository.class);
        when(repository.findCatalog()).thenReturn(List.of());

        assertEquals("", new EmotionService(repository).blockFor(1));
    }

    /** Базовые значения из миграции 006: на них спокойная персона даёт агрессию 0. */
    private static Map<String, Integer> baseValues() {
        Map<String, Integer> values = new HashMap<>();
        for (Emotion emotion : CATALOG) {
            if (!emotion.derived())
                values.put(emotion.code(), emotion.baseDefault());
        }
        return values;
    }
}
