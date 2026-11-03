package org.example.AiChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.Settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.example.Settings.Settings.AI_PLAN_MAX_TOKENS;
import static org.example.Settings.Settings.AI_SYSTEM_ROLE;
import static org.example.Settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnswerPlannerTest {

    private static final long CHAT_ID = 7L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private PromptComposer composer;
    @Mock
    private AiHistory history;
    @Mock
    private DeepSeekClient deepSeekClient;
    @Mock
    private SettingsService settings;
    @Mock
    private PersonaGrudgeService grudges;

    private AnswerPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new AnswerPlanner(composer, history, deepSeekClient, settings, grudges, new AnswerPlanParser());

        when(composer.composeForPlan(CHAT_ID)).thenReturn(
                new PromptComposer.ComposedPrompt(List.of(), "КОНТЕКСТ БЕЗ ПЕРСОНЫ"));
        when(composer.memoryPool(CHAT_ID)).thenReturn(new PromptComposer.MemoryPool(
                List.of(),
                List.of(new MemoryRecord(2, "хобби", "шахматы", 6, false, null, List.of()))));
        when(grudges.targeted(CHAT_ID)).thenReturn(List.of(
                new PersonaGrudgeService.Grudge(111L, "@nick (Ник)", 6)));
        when(history.message(anyString(), anyString())).thenAnswer(invocation ->
                MAPPER.createObjectNode()
                        .put("role", (String) invocation.getArgument(0))
                        .put("content", (String) invocation.getArgument(1)));
        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", USER_ROLE).put("content", "@nick (Ник): привет"));
        when(history.messages(CHAT_ID)).thenReturn(messages);
        when(settings.getInt(AI_PLAN_MAX_TOKENS, AnswerPlanner.DEFAULT_PLAN_MAX_TOKENS)).thenReturn(1500);
    }

    @Test
    void planRequestContainsPromptMemoryAndGrudges() {
        when(deepSeekClient.sendRequestToAi(any(), eq(0.2f), eq(1500))).thenReturn(
                "{\"topic\":\"приветствие\",\"intent\":\"remark\",\"replies\":2}");

        Optional<AnswerPlan> plan = planner.plan(CHAT_ID, 4);

        assertTrue(plan.isPresent());
        assertEquals(PlanIntent.REMARK, plan.get().intent());
        assertEquals(2, plan.get().replies());

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), eq(0.2f), eq(1500));
        ArrayNode messages = payload.getValue();

        assertEquals(AI_SYSTEM_ROLE, messages.get(0).get("role").asText());
        assertEquals("КОНТЕКСТ БЕЗ ПЕРСОНЫ", messages.get(0).get("content").asText());
        String instruction = messages.get(1).get("content").asText();
        assertTrue(instruction.contains("Ты планировщик"), instruction);
        assertTrue(instruction.contains("2 | хобби | шахматы"), instruction);
        assertTrue(instruction.contains("@nick (Ник), сила 6"), instruction);
        assertEquals("@nick (Ник): привет", messages.get(2).get("content").asText());
        assertEquals(USER_ROLE, messages.get(messages.size() - 1).get("role").asText());
        assertTrue(messages.get(messages.size() - 1).get("content").asText().contains("Составь план"),
                messages.get(messages.size() - 1).get("content").asText());
    }

    @Test
    void emptyModelAnswerGivesEmptyPlan() {
        when(deepSeekClient.sendRequestToAi(any(), eq(0.2f), anyInt())).thenReturn(null);

        assertTrue(planner.plan(CHAT_ID, 4).isEmpty());
    }

    @Test
    void brokenPlanGivesEmptyPlan() {
        when(deepSeekClient.sendRequestToAi(any(), eq(0.2f), anyInt())).thenReturn("не json");

        assertTrue(planner.plan(CHAT_ID, 4).isEmpty());
    }

    @Test
    void planTokenLimitFallsBackWhenSettingIsBroken() {
        when(settings.getInt(AI_PLAN_MAX_TOKENS, AnswerPlanner.DEFAULT_PLAN_MAX_TOKENS)).thenReturn(0);
        when(deepSeekClient.sendRequestToAi(any(), eq(0.2f), anyInt())).thenReturn(
                "{\"intent\":\"remark\",\"replies\":1}");

        planner.plan(CHAT_ID, 4);

        verify(deepSeekClient).sendRequestToAi(any(), eq(0.2f), eq(AnswerPlanner.DEFAULT_PLAN_MAX_TOKENS));
    }
}
