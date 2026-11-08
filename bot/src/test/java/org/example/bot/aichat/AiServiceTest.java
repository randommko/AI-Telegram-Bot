package org.example.bot.aichat;

import org.example.core.aichat.AiChatRepository;
import org.example.core.aichat.AiHistory;
import org.example.core.aichat.AiMessage;
import org.example.core.aichat.EmotionService;
import org.example.core.aichat.EmotionState;
import org.example.core.aichat.MessageAuthor;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaSettings;
import org.example.core.aichat.PromptComposer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.bot.Delay;
import org.example.bot.MessageSender;
import org.example.core.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.example.core.settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.core.settings.Settings.AI_ASSISTANT_ROLE;
import static org.example.core.settings.Settings.AI_SYSTEM_ROLE;
import static org.example.core.settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceTest {

    private static final long CHAT_ID = 7L;
    private static final int PERSONA_ID = 3;
    private static final MessageAuthor AUTHOR = new MessageAuthor(111L, "@nick", "Ник");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private SettingsService settings;
    @Mock
    private MessageSender sender;
    @Mock
    private LlmClient llmClient;
    @Mock
    private AiChatRepository repository;
    @Mock
    private PromptComposer promptComposer;
    @Mock
    private AiHistory history;
    @Mock
    private AnswerPlanner planner;
    @Mock
    private AnswerGenerator generator;
    @Mock
    private PersonaRepository personaRepository;
    @Mock
    private PersonaGrudgeService grudges;
    @Mock
    private EmotionService emotionService;
    @Mock
    private HysteriaService hysteriaService;
    @Mock
    private Delay delay;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiService(settings, sender, llmClient, repository, promptComposer, history,
                planner, generator, personaRepository, grudges, emotionService, hysteriaService, delay);

        when(settings.getFloat(AI_ANSWER_TEMPERATURE, 1.0f)).thenReturn(1.0f);
        when(history.size()).thenReturn(100);
        when(history.messages(CHAT_ID)).thenReturn(MAPPER.createArrayNode());
        when(history.message(anyString(), anyString())).thenAnswer(invocation ->
                MAPPER.createObjectNode()
                        .put("role", (String) invocation.getArgument(0))
                        .put("content", (String) invocation.getArgument(1)));
        when(promptComposer.compose(CHAT_ID)).thenReturn(new PromptComposer.ComposedPrompt(List.of(), "СИСТЕМА"));
        when(personaRepository.findSettingsForChat(CHAT_ID)).thenReturn(Optional.of(persona()));
        when(emotionService.stateFor(PERSONA_ID)).thenReturn(
                new EmotionState(List.of(), 2, "Агрессия", "спокойно"));
        when(hysteriaService.shouldStart(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        when(repository.findLastMessages(CHAT_ID, 1)).thenReturn(List.of(
                new AiMessage(USER_ROLE, "вопрос?",
                        new MessageAuthor(111L, "@nick", "Ник"))));
        when(repository.findLastMessages(CHAT_ID, 50)).thenReturn(List.of(
                new AiMessage(USER_ROLE, "вопрос?",
                        new MessageAuthor(111L, "@nick", "Ник")),
                new AiMessage(USER_ROLE, "старое",
                        new MessageAuthor(222L, "@ann", "Анна"))));
    }

    @Test
    void threeRepliesAreSentWithDelaysAndSavedSeparately() {
        AnswerPlan plan = plan(PlanIntent.QUESTION, Map.of(), 3);
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan));
        when(generator.generate(eq(CHAT_ID), eq(plan), eq(3), anyList(), eq(false), anyFloat()))
                .thenReturn(List.of("раз", "два", "три"));

        aiService.askAi(CHAT_ID, "вопрос?");

        verify(sender).sendLongMessage(CHAT_ID, "раз", null);
        verify(sender).sendLongMessage(CHAT_ID, "два", null);
        verify(sender).sendLongMessage(CHAT_ID, "три", null);
        verify(delay, times(2)).pause(1500);
        verify(repository).saveMessage(CHAT_ID, AI_ASSISTANT_ROLE, "раз", 100);
        verify(repository).saveMessage(CHAT_ID, AI_ASSISTANT_ROLE, "два", 100);
        verify(repository).saveMessage(CHAT_ID, AI_ASSISTANT_ROLE, "три", 100);
    }

    @Test
    void ignoreSendsNothing() {
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan(PlanIntent.IGNORE, Map.of(), 0)));

        aiService.askAi(CHAT_ID, "ну и?");

        verify(generator, never()).generate(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(),
                anyList(), org.mockito.ArgumentMatchers.anyBoolean(), anyFloat());
        verify(sender, never()).sendLongMessage(anyLong(), anyString(), any());
        verify(sender, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void planFailureFallsBackToOneRequest() {
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.empty());
        when(llmClient.sendRequestToAi(any(), eq(1.0f))).thenReturn("ответ фолбэка");

        aiService.askAi(CHAT_ID, "вопрос?");

        verify(llmClient).sendRequestToAi(any(), eq(1.0f));
        verify(sender, never()).sendMessage(anyLong(), anyString());
        verify(sender).sendLongMessage(CHAT_ID, "ответ фолбэка", null);
        verify(repository).saveMessage(CHAT_ID, AI_ASSISTANT_ROLE, "ответ фолбэка", 100);
    }

    @Test
    void emptyGenerationFallsBack() {
        AnswerPlan plan = plan(PlanIntent.QUESTION, Map.of(), 2);
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan));
        when(generator.generate(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), anyList(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyFloat())).thenReturn(List.of());
        when(llmClient.sendRequestToAi(any(), eq(1.0f))).thenReturn("ответ фолбэка");

        aiService.askAi(CHAT_ID, "вопрос?");

        verify(sender).sendLongMessage(CHAT_ID, "ответ фолбэка", null);
    }

    @Test
    void chatWithoutPersonaUsesFallback() {
        when(personaRepository.findSettingsForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(llmClient.sendRequestToAi(any(), eq(1.0f))).thenReturn("ответ");

        aiService.askAi(CHAT_ID, "привет");

        verify(planner, never()).plan(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(sender).sendLongMessage(CHAT_ID, "ответ", null);
    }

    @Test
    void failedFallbackNotifiesChatOnce() {
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.empty());
        when(llmClient.sendRequestToAi(any(), eq(1.0f))).thenReturn(null);

        aiService.askAi(CHAT_ID, "вопрос?");

        verify(sender, times(1)).sendMessage(CHAT_ID, AiService.AI_UNAVAILABLE_NOTICE, null);
        verify(sender, never()).sendLongMessage(anyLong(), anyString(), any());
    }

    @Test
    void chatWithoutPersonaNotifiesOnFailure() {
        when(personaRepository.findSettingsForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(llmClient.sendRequestToAi(any(), eq(1.0f))).thenReturn(null);

        aiService.askAi(CHAT_ID, "привет");

        verify(sender).sendMessage(CHAT_ID, AiService.AI_UNAVAILABLE_NOTICE, null);
        verify(sender, never()).sendLongMessage(anyLong(), anyString(), any());
    }

    @Test
    void blankFallbackAnswerIsFailure() {
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.empty());
        when(llmClient.sendRequestToAi(any(), eq(1.0f))).thenReturn("");

        aiService.askAi(CHAT_ID, "вопрос?");

        verify(sender).sendMessage(CHAT_ID, AiService.AI_UNAVAILABLE_NOTICE, null);
        verify(sender, never()).sendLongMessage(anyLong(), anyString(), any());
    }

    @Test
    void notificationIsNotSavedToHistory() {
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.empty());
        when(llmClient.sendRequestToAi(any(), eq(1.0f))).thenReturn(null);

        aiService.askAi(CHAT_ID, "вопрос?");

        verify(repository, never()).saveMessage(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    void insultRaisesGrudgeAndAnger() {
        AnswerPlan plan = plan(PlanIntent.INSULT, Map.of("гнев", 1), 1);
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan));
        when(generator.generate(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), anyList(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyFloat())).thenReturn(List.of("ответ"));
        when(grudges.angerDelta()).thenReturn(2);
        when(emotionService.applyShifts(eq(PERSONA_ID), any())).thenReturn(Map.of("гнев", 3));

        aiService.askAi(CHAT_ID, "ты никто", null, AUTHOR);

        verify(grudges).insult(CHAT_ID, 111L, "ты никто");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emotionService).applyShifts(eq(PERSONA_ID), captor.capture());
        assertEquals(3, captor.getValue().get("гнев"));
    }

    @Test
    void apologyRemovesGrudge() {
        AnswerPlan plan = plan(PlanIntent.APOLOGY, Map.of(), 1);
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan));
        when(generator.generate(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), anyList(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyFloat())).thenReturn(List.of("ладно"));

        aiService.askAi(CHAT_ID, "извини", null, AUTHOR);

        verify(grudges).forgive(CHAT_ID, 111L);
        verify(grudges, never()).insult(anyLong(), anyLong(), anyString());
    }

    @Test
    void replyAttachesOnlyToFirstReply() {
        AnswerPlan plan = plan(PlanIntent.QUESTION, Map.of(), 2);
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan));
        when(generator.generate(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), anyList(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyFloat())).thenReturn(List.of("раз", "два"));

        aiService.askAi(CHAT_ID, "вопрос?", 555, AUTHOR);

        verify(sender).sendLongMessage(CHAT_ID, "раз", 555);
        verify(sender).sendLongMessage(CHAT_ID, "два", null);
    }

    @Test
    void grudgeIsRecordedForTriggerAuthorNotHistory() {
        AnswerPlan plan = plan(PlanIntent.INSULT, Map.of(), 1);
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan));
        when(generator.generate(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), anyList(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyFloat())).thenReturn(List.of("ответ"));
        when(grudges.angerDelta()).thenReturn(2);
        when(emotionService.applyShifts(eq(PERSONA_ID), any())).thenReturn(Map.of());

        // В истории последним лежит автор 111, а сообщение-триггер написал 222.
        aiService.askAi(CHAT_ID, "ты никто", 555, new MessageAuthor(222L, "@ann", "Анна"));

        verify(grudges).insult(CHAT_ID, 222L, "ты никто");
        verify(grudges, never()).insult(anyLong(), eq(111L), anyString());
    }

    @Test
    void hysteriaOverridesRepliesTargetsAndResetsAnger() {
        AnswerPlan plan = plan(PlanIntent.INSULT, Map.of(), 2);
        when(planner.plan(eq(CHAT_ID), eq(4), any())).thenReturn(Optional.of(plan));
        when(emotionService.stateFor(PERSONA_ID)).thenReturn(
                new EmotionState(List.of(), 9, "Агрессия", "психоз"));
        when(hysteriaService.shouldStart(any(), eq(PlanIntent.INSULT), eq(9))).thenReturn(true);
        when(generator.generate(eq(CHAT_ID), eq(plan), eq(4), anyList(), eq(true), anyFloat()))
                .thenReturn(List.of("а", "б", "в", "г"));

        aiService.askAi(CHAT_ID, "ты никто");

        verify(generator).generate(eq(CHAT_ID), eq(plan), eq(4), anyList(), eq(true), anyFloat());
        verify(hysteriaService).record(PERSONA_ID);
    }

    @Test
    void blankQuestionIsNotSentToAi() {
        aiService.askAi(CHAT_ID, " ");

        verifyNoInteractions(planner, generator, llmClient);
        verify(sender).sendMessage(CHAT_ID, "Напиши свой вопрос после команды /ai");
    }

    @Test
    void historyLimitComesFromSettings() {
        when(history.size()).thenReturn(50);

        aiService.saveMessage(5L, USER_ROLE, "текст");

        verify(repository).saveMessage(5L, USER_ROLE, "текст", 50);
    }

    @Test
    void assistantAnswerGoesWithoutAuthor() {
        when(repository.countMessages(1L)).thenReturn(0);

        assertEquals(0, aiService.getChatHistorySize(1L));
    }

    private static PersonaSettings persona() {
        return new PersonaSettings(PERSONA_ID, 4, 1500, 8, 4, 6, null);
    }

    private static AnswerPlan plan(PlanIntent intent, Map<String, Integer> deltas, int replies) {
        return new AnswerPlan("тема", intent, List.of(), deltas, "причина", replies, List.of());
    }

    private Message message(String text, Long chatId) {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        return message;
    }
}
