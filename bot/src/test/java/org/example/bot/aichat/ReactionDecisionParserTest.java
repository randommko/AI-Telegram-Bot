package org.example.bot.aichat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionDecisionParserTest {
    private final ReactionDecisionParser parser = new ReactionDecisionParser();
    private final Set<Integer> messages = Set.of(101, 102, 103);
    private final Set<String> emojis = Set.of("🔥", "🤡", "😁");

    @Test
    void parsesTwoReactions() {
        String raw = "{\"reactions\":["
                + "{\"message_id\":101,\"emoji\":\"🔥\",\"reason\":\"сильно\"},"
                + "{\"message_id\":102,\"emoji\":\"🤡\",\"reason\":\"клоун\"}]}";

        List<ReactionDecisionParser.ReactionDecision> result = parser.parse(raw, messages, emojis);

        assertEquals(2, result.size());
        assertEquals(101, result.get(0).messageId());
        assertEquals("🔥", result.get(0).emoji());
        assertEquals("сильно", result.get(0).reason());
    }

    @Test
    void stripsMarkdownFences() {
        String raw = "```json\n{\"reactions\":[{\"message_id\":103,\"emoji\":\"😁\"}]}\n```";

        assertEquals(1, parser.parse(raw, messages, emojis).size());
    }

    @Test
    void dropsEmojiOutsideActiveCatalog() {
        String raw = "{\"reactions\":[{\"message_id\":101,\"emoji\":\"👍\"}]}";

        assertTrue(parser.parse(raw, messages, emojis).isEmpty());
    }

    @Test
    void dropsMessageOutsideBatch() {
        String raw = "{\"reactions\":[{\"message_id\":999,\"emoji\":\"🔥\"}]}";

        assertTrue(parser.parse(raw, messages, emojis).isEmpty());
    }

    @Test
    void keepsNoMoreThanTwo() {
        String raw = "{\"reactions\":["
                + "{\"message_id\":101,\"emoji\":\"🔥\"},"
                + "{\"message_id\":102,\"emoji\":\"🤡\"},"
                + "{\"message_id\":103,\"emoji\":\"😁\"}]}";

        assertEquals(2, parser.parse(raw, messages, emojis).size());
    }

    @Test
    void duplicateMessageCountsOnce() {
        String raw = "{\"reactions\":["
                + "{\"message_id\":101,\"emoji\":\"🔥\"},"
                + "{\"message_id\":101,\"emoji\":\"🤡\"}]}";

        List<ReactionDecisionParser.ReactionDecision> result = parser.parse(raw, messages, emojis);

        assertEquals(1, result.size());
        assertEquals("🔥", result.get(0).emoji());
    }

    @Test
    void malformedJsonGivesEmptyList() {
        assertTrue(parser.parse("не json", messages, emojis).isEmpty());
        assertTrue(parser.parse("", messages, emojis).isEmpty());
        assertTrue(parser.parse("{\"other\":[]}", messages, emojis).isEmpty());
    }
}
