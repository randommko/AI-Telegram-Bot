package org.example.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSplitterTest {

    @Test
    void shortTextStaysOnePart() {
        assertEquals(List.of("короткий ответ"), TextSplitter.split("короткий ответ", 100));
    }

    @Test
    void textExactlyAtLimitIsNotSplit() {
        String text = "a".repeat(50);

        assertEquals(List.of(text), TextSplitter.split(text, 50));
    }

    @Test
    void blankTextGivesNoParts() {
        assertTrue(TextSplitter.split("  \n ", 100).isEmpty());
        assertTrue(TextSplitter.split(null, 100).isEmpty());
    }

    @Test
    void paragraphsArePreferredAsBoundaries() {
        String text = "Первый абзац текста тут.\n\nВторой абзац тоже тут и он длиннее.";

        List<String> parts = TextSplitter.split(text, 30);

        assertTrue(parts.size() >= 2, parts.toString());
        assertEquals("Первый абзац текста тут.", parts.get(0));
        assertTrue(parts.stream().allMatch(part -> part.length() <= 30), parts.toString());
    }

    @Test
    void sentencesAreUsedWhenThereAreNoParagraphs() {
        String text = "Это коротко. Второе предложение тут очень длинное и ещё длиннее.";

        List<String> parts = TextSplitter.split(text, 20);

        assertEquals("Это коротко.", parts.get(0));
        assertTrue(parts.stream().allMatch(part -> part.length() <= 20), parts.toString());
    }

    @Test
    void textWithoutBoundariesIsHardCut() {
        String text = "a".repeat(55);

        List<String> parts = TextSplitter.split(text, 20);

        assertEquals(3, parts.size(), parts.toString());
        assertEquals(20, parts.get(0).length());
        assertEquals(20, parts.get(1).length());
        assertEquals(15, parts.get(2).length());
    }

    @Test
    void noPartExceedsTheLimitAndNothingIsLost() {
        String text = ("Живой текст без точек и запятых но с пробелами ").repeat(10);

        List<String> parts = TextSplitter.split(text, 40);

        assertTrue(parts.stream().allMatch(part -> part.length() <= 40), parts.toString());
        String joined = String.join(" ", parts).replaceAll("\\s+", " ").trim();
        String original = text.replaceAll("\\s+", " ").trim();
        assertEquals(original, joined);
        assertFalse(parts.isEmpty());
    }
}
