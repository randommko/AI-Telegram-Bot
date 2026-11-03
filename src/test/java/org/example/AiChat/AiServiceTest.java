package org.example.AiChat;

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

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_ASSISTANT_ROLE;
import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_SYSTEM_ROLE;
import static org.example.Settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private PromptComposer promptComposer;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiService(settings, sender, deepSeekClient, repository, promptComposer);
    }

    @Test
    void temperatureIsReadOnEveryRequest() {
        when(promptComposer.compose(anyLong())).thenReturn(prompt("Промпт"));
        when(settings.getFloat(AI_ANSWER_TEMPERATURE, 1.0f)).thenReturn(1.0f, 1.9f);
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(7L, "первый");
        aiService.askAi(7L, "второй");

        verify(deepSeekClient).sendRequestToAi(any(), eq(1.0f));
        verify(deepSeekClient).sendRequestToAi(any(), eq(1.9f));
    }

    @Test
    void composedPromptGoesFirstAsSingleSystemMessage() {
        when(promptComposer.compose(7L)).thenReturn(prompt("Техника\n\n## Кто ты\nТы кот из чата"));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("50");
        when(repository.findLastMessages(7L, 50)).thenReturn(List.of(new AiMessage(USER_ROLE, "привет")));
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(message("/ai как дела", 7L));

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());

        ArrayNode messages = payload.getValue();
        assertEquals(2, messages.size(), "один системный промпт и история");
        assertEquals(AI_SYSTEM_ROLE, messages.get(0).get("role").asText());
        assertEquals("Техника\n\n## Кто ты\nТы кот из чата", messages.get(0).get("content").asText());
        assertEquals(USER_ROLE, messages.get(1).get("role").asText());
        assertEquals("привет", messages.get(1).get("content").asText());
    }

    @Test
    void promptChangeIsVisibleInNextRequest() {
        when(promptComposer.compose(anyLong()))
                .thenReturn(prompt("Первый промпт"))
                .thenReturn(prompt("Второй промпт"));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
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
    void historyIsRenderedWithAuthorFromColumns() {
        when(promptComposer.compose(17L)).thenReturn(prompt("промпт"));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.findLastMessages(17L, 100)).thenReturn(List.of(
                new AiMessage(USER_ROLE, "как дела?", new MessageAuthor(111L, "@nick", "Ник"))));
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ");

        aiService.askAi(17L, "как дела?");

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        ArrayNode messages = payload.getValue();
        assertEquals("@nick (Ник): как дела?", messages.get(messages.size() - 1).get("content").asText());
    }

    @Test
    void assistantMessagesGoWithoutAuthorPrefix() {
        when(promptComposer.compose(18L)).thenReturn(prompt("промпт"));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.findLastMessages(18L, 100)).thenReturn(List.of(
                new AiMessage(AI_ASSISTANT_ROLE, "прошлый ответ")));
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn(null);

        aiService.askAi(18L, "привет");

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(deepSeekClient).sendRequestToAi(payload.capture(), any());
        ArrayNode messages = payload.getValue();
        assertEquals("прошлый ответ", messages.get(messages.size() - 1).get("content").asText());
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
    void authorIsPassedToRepository() {
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        MessageAuthor author = new MessageAuthor(111L, "@nick", "Ник");

        aiService.saveMessage(5L, USER_ROLE, "текст", author);

        verify(repository).saveMessage(5L, USER_ROLE, "текст", 100, author);
    }

    @Test
    void assistantAnswerIsSentWholeAndSavedOnce() {
        when(promptComposer.compose(13L)).thenReturn(prompt("промпт"));
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(repository.findLastMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(deepSeekClient.sendRequestToAi(any(), any())).thenReturn("ответ AI");

        aiService.askAi(message("/ai вопрос", 13L));

        verify(sender).sendLongMessage(13L, "ответ AI");
        verify(repository).saveMessage(eq(13L), eq(AI_ASSISTANT_ROLE), eq("ответ AI"), eq(100));
        verify(sender, never()).sendMessage(anyLong(), any());
    }

    @Test
    void blankQuestionIsNotSentToAi() {
        aiService.askAi(17L, " ");

        verifyNoInteractions(deepSeekClient);
        verify(sender).sendMessage(17L, "Напиши свой вопрос после команды /ai");
    }

    private static PromptComposer.ComposedPrompt prompt(String text) {
        return new PromptComposer.ComposedPrompt(List.of(), text);
    }

    private Message message(String text, Long chatId) {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        return message;
    }
}
