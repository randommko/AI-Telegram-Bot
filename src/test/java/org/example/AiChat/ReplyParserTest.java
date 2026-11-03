package org.example.AiChat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyParserTest {

    private final ReplyParser parser = new ReplyParser();

    @Test
    void parsesArrayOfThreeReplies() {
        assertEquals(List.of("раз", "два", "три"), parser.parse("[\"раз\",\"два\",\"три\"]", 4));
    }

    @Test
    void parsesObjectWrapper() {
        assertEquals(List.of("раз", "два"), parser.parse("{\"replies\":[\"раз\",\"два\"]}", 4));
    }

    @Test
    void stripsMarkdownFence() {
        assertEquals(List.of("раз"), parser.parse("```json\n[\"раз\"]\n```", 4));
    }

    @Test
    void dropsEmptyReplies() {
        assertEquals(List.of("раз"), parser.parse("[\"раз\",\"  \",\"\"]", 4));
    }

    @Test
    void keepsOnlyTheLimit() {
        List<String> replies = parser.parse("[\"1\",\"2\",\"3\",\"4\",\"5\"]", 3);

        assertEquals(List.of("1", "2", "3"), replies);
    }

    @Test
    void stripsTextAroundArray() {
        assertEquals(List.of("раз", "два"),
                parser.parse("Вот реплики: [\"раз\",\"два\"] — готово", 4));
    }

    @Test
    void brokenAnswerGivesNoReplies() {
        assertTrue(parser.parse("не json", 4).isEmpty());
        assertTrue(parser.parse(null, 4).isEmpty());
        assertTrue(parser.parse("{\"text\":\"одна строка\"}", 4).isEmpty());
    }
}
