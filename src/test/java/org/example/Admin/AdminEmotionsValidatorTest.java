package org.example.Admin;

import org.example.AiChat.Emotion;
import org.example.AiChat.EmotionPhrase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminEmotionsValidatorTest {

    private final AdminEmotionsValidator validator = new AdminEmotionsValidator();

    private static final Emotion ANGER = new Emotion("гнев", "Гнев", "Злость", "минус",
            0.55, 1.5, 2, false, 30);
    private static final Emotion AGGRESSION = new Emotion("агрессия", "Агрессия", "Производная", "стиль",
            0, 0, 0, true, 999);
    private static final List<Emotion> CATALOG = List.of(ANGER, AGGRESSION);
    private static final Map<String, List<EmotionPhrase>> PHRASES = Map.of(
            "гнев", List.of(new EmotionPhrase("гнев", 5, 6, "Ты злишься")));

    @Test
    void validValuesAreParsed() {
        var result = validator.validate(CATALOG, PHRASES, Map.of(
                "title_гнев", "Злость",
                "weight_гнев", "-0.4",
                "decay_гнев", "2",
                "base_гнев", "7",
                "phrase_гнев_5", "Новая фраза"));

        assertFalse(result.hasErrors(), result.errors().toString());
        assertEquals(1, result.emotions().size());
        assertEquals("Злость", result.emotions().get(0).title());
        assertEquals(-0.4, result.emotions().get(0).weight());
        assertEquals(2.0, result.emotions().get(0).decay());
        assertEquals(7, result.emotions().get(0).base());
        assertEquals("Новая фраза", result.phrases().get(0).phrase());
    }

    @Test
    void derivedEmotionIsNotEditable() {
        var result = validator.validate(CATALOG, Map.of(), Map.of());

        assertEquals(1, result.emotions().size());
        assertEquals("гнев", result.emotions().get(0).emotion().code());
    }

    @Test
    void weightOutsideRangeIsRejected() {
        var result = validator.validate(CATALOG, PHRASES, Map.of("weight_гнев", "2"));

        assertTrue(result.errors().containsKey("weight_гнев"), result.errors().toString());
        assertEquals(0.55, result.emotions().get(0).weight(), "остаётся прежнее значение");
    }

    @Test
    void baseOutsideScaleIsRejected() {
        var result = validator.validate(CATALOG, PHRASES, Map.of("base_гнев", "11"));

        assertTrue(result.errors().containsKey("base_гнев"), result.errors().toString());
    }

    @Test
    void negativeDecayIsRejected() {
        var result = validator.validate(CATALOG, PHRASES, Map.of("decay_гнев", "-1"));

        assertTrue(result.errors().containsKey("decay_гнев"), result.errors().toString());
    }

    @Test
    void emptyPhraseIsRejected() {
        var result = validator.validate(CATALOG, PHRASES, Map.of("phrase_гнев_5", "  "));

        assertTrue(result.errors().containsKey("phrase_гнев_5"), result.errors().toString());
    }

    @Test
    void emptyTitleIsRejected() {
        var result = validator.validate(CATALOG, PHRASES, Map.of("title_гнев", " "));

        assertTrue(result.errors().containsKey("title_гнев"), result.errors().toString());
    }
}
