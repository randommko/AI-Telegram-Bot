package org.example.AiChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.example.Settings.Settings.AI_SYSTEM_ROLE;
import static org.example.Settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnswerGeneratorTest {

    private static final long CHAT_ID = 7L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AnswerPlan PLAN = new AnswerPlan("тема", PlanIntent.QUESTION,
            List.of(2), Map.of("гнев", 1), "причина", 3, List.of("@nick"));

    @Mock
    private PromptComposer composer;
    @Mock
    private AiHistory history;
    @Mock
    private DeepSeekClient deepSeekClient;

    private AnswerGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AnswerGenerator(composer, history, deepSeekClient, new ReplyParser());

        when(composer.compose(CHAT_ID, List.of(2))).thenReturn(
                new PromptComposer.ComposedPrompt(List.of(), "СИСТЕМА"));
        when(history.message(anyString(), anyString())).thenAnswer(invocation ->
                MAPPER.createObjectNode()
                        .put("role", (String) invocation.getArgument(0))
                        .put("content", (String) invocation.getArgument(1)));
        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", USER_ROLE).put("content", "@nick: привет"));
        when(history.messages(CHAT_ID)).thenReturn(messages);
    }

    @Test
    void generationRequestContainsPlanAndTargets() {
        when(deepSeekClient.sendJsonRequestToAi(any(), anyFloat())).thenReturn("[\"раз\",\"два\",\"три\"]");

        List<String> replies = generator.generate(CHAT_ID, PLAN, 3, List.of("@nick"), false, 1.0f);

        assertEquals(List.of("раз", "два", "три"), replies);

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendJsonRequestToAi(payload.capture(), eq(1.0f));
        ArrayNode messages = payload.getValue();
        assertEquals(AI_SYSTEM_ROLE, messages.get(0).get("role").asText());
        assertEquals("СИСТЕМА", messages.get(0).get("content").asText());
        String instruction = messages.get(1).get("content").asText();
        assertTrue(instruction.contains("реплик 3"), instruction);
        assertTrue(instruction.contains("Адресаты колкостей: @nick"), instruction);
        assertEquals("@nick: привет", messages.get(2).get("content").asText());
        assertEquals(USER_ROLE, messages.get(messages.size() - 1).get("role").asText());
        assertTrue(messages.get(messages.size() - 1).get("content").asText().contains("replies"),
                messages.get(messages.size() - 1).get("content").asText());
    }

    @Test
    void hysteriaIsMentionedInInstruction() {
        when(deepSeekClient.sendJsonRequestToAi(any(), anyFloat())).thenReturn("[\"раз\"]");

        generator.generate(CHAT_ID, PLAN, 4, List.of("@nick"), true, 1.0f);

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendJsonRequestToAi(payload.capture(), eq(1.0f));
        assertTrue(payload.getValue().get(1).get("content").asText().contains("срыв"));
    }

    @Test
    void emptyAnswerGivesNoReplies() {
        when(deepSeekClient.sendJsonRequestToAi(any(), anyFloat())).thenReturn(null);

        assertTrue(generator.generate(CHAT_ID, PLAN, 3, List.of(), false, 1.0f).isEmpty());
    }

    @Test
    void zeroRepliesDoNotCallModel() {
        assertTrue(generator.generate(CHAT_ID, PLAN, 0, List.of(), false, 1.0f).isEmpty());
        verify(deepSeekClient, org.mockito.Mockito.never()).sendJsonRequestToAi(any(), anyFloat());
    }
}
