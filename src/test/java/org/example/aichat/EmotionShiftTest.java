package org.example.aichat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmotionShiftTest {

    @Test
    void shiftIsAppliedToEffectiveValue() {
        assertEquals(5, EmotionService.shiftValue(3, 2, EmotionService.MAX_SHIFT));
    }

    @Test
    void shiftIsCappedAtThree() {
        assertEquals(6, EmotionService.shiftValue(3, 5, EmotionService.MAX_SHIFT));
        assertEquals(0, EmotionService.shiftValue(3, -5, EmotionService.MAX_SHIFT));
    }

    @Test
    void valueIsClampedToTen() {
        assertEquals(10, EmotionService.shiftValue(9, 3, EmotionService.MAX_SHIFT));
    }

    @Test
    void valueIsClampedToZero() {
        assertEquals(0, EmotionService.shiftValue(1, -3, EmotionService.MAX_SHIFT));
    }

    @Test
    void oneStepCannotChangeMoreThanThree() {
        Map<String, Integer> deltas = Map.of("гнев", 2, "радость", -1);

        assertEquals(5, EmotionService.shiftValue(3, deltas.get("гнев"), EmotionService.MAX_SHIFT));
        assertEquals(4, EmotionService.shiftValue(5, deltas.get("радость"), EmotionService.MAX_SHIFT));
    }

    @Test
    void unknownEmotionCodeIsIgnored() {
        Map<String, Integer> effective = Map.of("гнев", 5);
        Map<String, Integer> deltas = Map.of("гнев", 2, "неизвестно", 3);

        Map<String, Integer> shifted = EmotionService.shiftAll(effective, deltas, EmotionService.MAX_SHIFT);

        assertEquals(Map.of("гнев", 7), shifted);
    }
}
