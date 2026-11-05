package org.example.bot.aichat;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerPlanParserTest {

    private final AnswerPlanParser parser = new AnswerPlanParser();

    @Test
    void parsesValidPlan() {
        String raw = """
                {"topic":"китайские тачки","intent":"question","memory_ids":[12,18],
                 "emotion":{"гнев":2,"радость":-1},"emotion_reason":"наехали на машину",
                 "replies":3,"targets":["@nick"]}
                """;

        AnswerPlan plan = parser.parse(raw, 4).orElseThrow();

        assertEquals("китайские тачки", plan.topic());
        assertEquals(PlanIntent.QUESTION, plan.intent());
        assertEquals(java.util.List.of(12, 18), plan.memoryIds());
        assertEquals(2, plan.emotionDeltas().get("гнев"));
        assertEquals(-1, plan.emotionDeltas().get("радость"));
        assertEquals("наехали на машину", plan.emotionReason());
        assertEquals(3, plan.replies());
        assertEquals(java.util.List.of("@nick"), plan.targets());
    }

    @Test
    void stripsMarkdownFence() {
        String raw = "```json\n{\"topic\":\"тема\",\"intent\":\"remark\",\"replies\":1}\n```";

        assertEquals(PlanIntent.REMARK, parser.parse(raw, 4).orElseThrow().intent());
    }

    @Test
    void brokenJsonGivesEmptyPlan() {
        assertTrue(parser.parse("{\"intent\":", 4).isEmpty());
        assertTrue(parser.parse("просто текст", 4).isEmpty());
        assertTrue(parser.parse("", 4).isEmpty());
        assertTrue(parser.parse(null, 4).isEmpty());
    }

    @Test
    void unknownIntentGivesEmptyPlan() {
        assertTrue(parser.parse("{\"intent\":\"shout\",\"replies\":1}", 4).isEmpty());
    }

    @Test
    void repliesOutsideRangeGiveEmptyPlan() {
        assertTrue(parser.parse("{\"intent\":\"question\",\"replies\":0}", 4).isEmpty());
        assertTrue(parser.parse("{\"intent\":\"question\",\"replies\":5}", 4).isEmpty());
        assertTrue(parser.parse("{\"intent\":\"question\",\"replies\":-1}", 4).isEmpty());
    }

    @Test
    void ignoreAlwaysHasZeroReplies() {
        AnswerPlan plan = parser.parse("{\"intent\":\"ignore\",\"replies\":5}", 4).orElseThrow();

        assertEquals(PlanIntent.IGNORE, plan.intent());
        assertEquals(0, plan.replies());
    }

    @Test
    void nonNumericDeltasAndNegativeIdsAreIgnored() {
        Optional<AnswerPlan> plan = parser.parse(
                "{\"intent\":\"question\",\"replies\":1,\"memory_ids\":[-3,7],"
                        + "\"emotion\":{\"гнев\":\"много\",\"радость\":1}}", 4);

        assertEquals(java.util.List.of(7), plan.orElseThrow().memoryIds());
        assertEquals(java.util.Set.of("радость"), plan.orElseThrow().emotionDeltas().keySet());
    }
}
