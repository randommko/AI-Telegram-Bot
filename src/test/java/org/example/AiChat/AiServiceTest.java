package org.example.AiChat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.example.Settings.Settings.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private SettingsService settings;
    @Mock
    private MessageSender sender;
    @Mock
    private DeepSeekClient deepSeekClient;
    @Mock
    private AiChatRepository repository;
    @Mock
    private PersonaService personaService;
    @Mock
    private MemoryRepository memoryRepository;

    private final MemoryPromptBuilder memoryPromptBuilder = new MemoryPromptBuilder();

    private AiService aiService;

    @BeforeEach
    void setUp() {
        when(settings.getSettingValue(AI_ANSWER_TEMPERATURE)).thenReturn("1.0");
        aiService = new AiService(settings, sender, deepSeekClient, repository,
                personaService, memoryRepository, memoryPromptBuilder);
    }

    @Test
    void systemPromptGoesFirstAndResetCounterIsIncluded() {
        when(personaService.resolvePrompt(7L)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Ты кот из чата", "setting:aiPromtContext")));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("50");
        when(repository.getResets(7L)).thenReturn(2);
        when(repository.findLastMessages(7L, 50)).thenReturn(List.of(new AiMessage(USER_ROLE, "привет")));
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(message("/ai как дела", 7L));

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());

        ArrayNode messages = payload.getValue();
        assertEquals(3, messages.size(), "промпт + счётчик сбросов + сохранённое сообщение");
        assertEquals(AI_SYSTEM_ROLE, messages.get(0).get("role").asText());
        assertEquals("Ты кот из чата", messages.get(0).get("content").asText());
        assertEquals(AI_SYSTEM_ROLE, messages.get(1).get("role").asText());
        assertEquals("Тебе обнуляли память уже 2 раз", messages.get(1).get("content").asText());
        assertEquals(USER_ROLE, messages.get(2).get("role").asText());
        assertEquals("привет", messages.get(2).get("content").asText());
    }

    @Test
    void promptChangeIsVisibleInNextRequest() {
        when(personaService.resolvePrompt(anyLong()))
                .thenReturn(Optional.of(new PersonaService.ResolvedPrompt("Первый промпт", "setting:aiPromtContext")))
                .thenReturn(Optional.of(new PersonaService.ResolvedPrompt("Второй промпт", "persona:Юра")));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.getResets(anyLong())).thenReturn(0);
        when(repository.findLastMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(message("/ai раз", 9L));
        aiService.askAi(message("/ai два", 9L));

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient, org.mockito.Mockito.times(2)).sendRequestToAi(payload.capture(), any());

        assertEquals("Первый промпт", payload.getAllValues().get(0).get(0).get("content").asText());
        assertEquals("Второй промпт", payload.getAllValues().get(1).get(0).get("content").asText());
    }

    @Test
    void fallbackPromptIsUsedWhenSettingIsEmpty() {
        when(personaService.resolvePrompt(anyLong())).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.getResets(anyLong())).thenReturn(0);
        when(repository.findLastMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn(null);

        aiService.askAi(message("/ai вопрос", 11L));

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        JsonNode prompt = payload.getValue().get(0);
        assertEquals(AI_SYSTEM_ROLE, prompt.get("role").asText());
        assertFalse(prompt.get("content").asText().isBlank());
    }

    @Test
    void personaPromptIsPreferredOverSettings() {
        when(personaService.resolvePrompt(23L)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Ты Юра", "persona:Юра")));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.getResets(23L)).thenReturn(0);
        when(repository.findLastMessages(23L, 100)).thenReturn(List.of());
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(23L, "привет");

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        assertEquals("Ты Юра", payload.getValue().get(0).get("content").asText());
    }

    @Test
    void emptyHistoryIsZeroMessagesAndResetDoesNotThrow() {
        when(repository.countMessages(1L)).thenReturn(0);

        assertEquals(0, aiService.getChatHistorySize(1L));

        aiService.clearChatHistory(1L);
        verify(repository).clearMessages(1L);
        verify(repository).incrementResets(1L);
    }

    @Test
    void historyLimitComesFromSettings() {
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");

        aiService.saveMessage(5L, USER_ROLE, "текст");

        verify(repository).saveMessage(5L, USER_ROLE, "текст", 100);
    }

    @Test
    void historyLimitIsNotHardcoded() {
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("50");

        aiService.saveMessage(5L, USER_ROLE, "текст");

        verify(repository).saveMessage(5L, USER_ROLE, "текст", 50);
    }

    @Test
    void historyLimitFallsBackWhenSettingIsMissing() {
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn(null);

        aiService.saveMessage(5L, USER_ROLE, "текст");

        verify(repository).saveMessage(5L, USER_ROLE, "текст", 100);
    }

    @Test
    void historyLimitFallsBackWhenSettingIsNotANumber() {
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("abc");

        aiService.saveMessage(5L, USER_ROLE, "текст");

        verify(repository).saveMessage(5L, USER_ROLE, "текст", 100);
    }

    @Test
    void historyLimitFallsBackWhenSettingIsZero() {
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("0");

        aiService.saveMessage(5L, USER_ROLE, "текст");

        verify(repository).saveMessage(5L, USER_ROLE, "текст", 100);
    }

    @Test
    void assistantAnswerIsSavedToHistory() {
        when(personaService.resolvePrompt(anyLong()))
                .thenReturn(Optional.of(new PersonaService.ResolvedPrompt("промпт", "setting:aiPromtContext")));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.getResets(anyLong())).thenReturn(0);
        when(repository.findLastMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ AI");

        aiService.askAi(message("/ai вопрос", 13L));

        verify(sender).sendMessage(13L, "ответ AI");
        verify(repository).saveMessage(eq(13L), eq(AI_ASSISTANT_ROLE), eq("ответ AI"), eq(100));
    }

    @Test
    void questionCanBeAskedWithoutAMessage() {
        when(personaService.resolvePrompt(anyLong()))
                .thenReturn(Optional.of(new PersonaService.ResolvedPrompt("промпт", "setting:aiPromtContext")));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.getResets(17L)).thenReturn(0);
        when(repository.findLastMessages(17L, 100)).thenReturn(List.of(new AiMessage(USER_ROLE, "как дела?")));
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(17L, "как дела?");

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        ArrayNode messages = payload.getValue();
        assertEquals("как дела?", messages.get(messages.size() - 1).get("content").asText(),
                "вопрос уходит в историю как есть, без префикса команды");
        verify(sender).sendMessage(17L, "ответ");
        verify(repository).saveMessage(eq(17L), eq(AI_ASSISTANT_ROLE), eq("ответ"), eq(100));
    }

    @Test
    void blankQuestionIsNotSentToAi() {
        aiService.askAi(17L, " ");

        verifyNoInteractions(deepSeekClient);
        verify(sender).sendMessage(17L, "Напиши свой вопрос после команды /ai");
    }

    @Test
    void memoryBlockGoesAfterPromptAndBeforeHistory() {
        when(personaService.resolvePrompt(31L)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Промпт персоны", "persona:Юра")));
        when(personaService.personaForChat(31L)).thenReturn(Optional.of(new Persona(7, "Юра", "Промпт персоны")));
        when(settings.getSettingValue(AI_MEMORY_PROMPT_SIZE)).thenReturn("30");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(memoryRepository.findCore(7)).thenReturn(List.of(
                new MemoryRecord(1, "семья", "родился 1988-04-12", 10, true, LocalDate.of(1988, 4, 12), List.of())));
        when(memoryRepository.findTopRegular(7, 30)).thenReturn(List.of(
                new MemoryRecord(2, "хобби", "шахматы", 6, false, null, List.of("nick"))));
        when(repository.getResets(31L)).thenReturn(0);
        when(repository.findLastMessages(31L, 100)).thenReturn(List.of(new AiMessage(USER_ROLE, "привет")));
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(31L, "привет");

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        ArrayNode messages = payload.getValue();

        assertEquals(3, messages.size(), "промпт, память и история");
        assertEquals("Промпт персоны", messages.get(0).get("content").asText());
        assertEquals(AI_SYSTEM_ROLE, messages.get(1).get("role").asText());
        String memory = messages.get(1).get("content").asText();
        assertTrue(memory.startsWith("Что ты помнишь о себе и о людях в чате всегда"), memory);
        assertTrue(memory.contains("[семья] родился 1988-04-12 (важно: 10, 1988-04-12)"), memory);
        assertTrue(memory.contains("[хобби] шахматы (важно: 6, @nick)"), memory);
        assertEquals(USER_ROLE, messages.get(2).get("role").asText());
    }

    @Test
    void noPersonaMeansNoMemoryBlock() {
        when(personaService.resolvePrompt(32L)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.getResets(32L)).thenReturn(0);
        when(repository.findLastMessages(32L, 100)).thenReturn(List.of(new AiMessage(USER_ROLE, "привет")));
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(32L, "привет");

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        assertEquals(2, payload.getValue().size(), "промпт и история");
        verifyNoInteractions(memoryRepository);
    }

    @Test
    void zeroMemorySizeSendsOnlyCore() {
        when(personaService.resolvePrompt(33L)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Промпт персоны", "persona:Юра")));
        when(personaService.personaForChat(33L)).thenReturn(Optional.of(new Persona(7, "Юра", "Промпт персоны")));
        when(settings.getSettingValue(AI_MEMORY_PROMPT_SIZE)).thenReturn("0");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(memoryRepository.findCore(7)).thenReturn(List.of(
                new MemoryRecord(1, "семья", "родился 1988-04-12", 10, true, null, List.of())));
        when(memoryRepository.findTopRegular(7, 0)).thenReturn(List.of());
        when(repository.getResets(33L)).thenReturn(0);
        when(repository.findLastMessages(33L, 100)).thenReturn(List.of());
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(33L, "привет");

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        String memory = payload.getValue().get(1).get("content").asText();
        assertTrue(memory.contains("[семья] родился 1988-04-12"), memory);
        assertFalse(memory.contains("[хобби]"), memory);
    }

    private Message message(String text, Long chatId) {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        return message;
    }
}
