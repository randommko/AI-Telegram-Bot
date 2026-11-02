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

import java.util.List;

import static org.example.Settings.Settings.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    private AiService aiService;

    @BeforeEach
    void setUp() {
        when(settings.getSettingValue(AI_ANSWER_TEMPERATURE)).thenReturn("1.0");
        aiService = new AiService(settings, sender, deepSeekClient, repository);
    }

    @Test
    void systemPromptGoesFirstAndResetCounterIsIncluded() {
        when(settings.getSettingValue(AI_CONTEXT)).thenReturn("Ты кот из чата");
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
        when(settings.getSettingValue(AI_CONTEXT)).thenReturn("Первый промпт", "Второй промпт");
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
        when(settings.getSettingValue(AI_CONTEXT)).thenReturn(null);
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
        when(settings.getSettingValue(AI_CONTEXT)).thenReturn("промпт");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.getResets(anyLong())).thenReturn(0);
        when(repository.findLastMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ AI");

        aiService.askAi(message("/ai вопрос", 13L));

        verify(sender).sendMessage(13L, "ответ AI");
        verify(repository).saveMessage(eq(13L), eq(AI_ASSISTANT_ROLE), eq("ответ AI"), eq(100));
    }

    private Message message(String text, Long chatId) {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        return message;
    }
}
