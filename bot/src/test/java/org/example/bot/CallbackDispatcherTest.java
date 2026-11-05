package org.example.bot;

import org.example.bot.aichat.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackDispatcherTest {

    private static final Long CHAT_ID = -1007777777777L;
    private static final Long AUTHOR_ID = 111L;
    private static final Long STRANGER_ID = 222L;
    private static final Integer MESSAGE_ID = 55;
    private static final int FORGOTTEN_COUNT = 7;

    @Mock
    private MessageSender messageSender;
    @Mock
    private AiService aiService;
    @Mock
    private TelegramLongPollingBot bot;

    private CallbackDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        when(messageSender.getBot()).thenReturn(bot);
        when(bot.execute(any(AnswerCallbackQuery.class))).thenReturn(true);
        dispatcher = new CallbackDispatcher(messageSender, aiService);
    }

    @Test
    void strangerPressGetsToastAndChangesNothing() throws Exception {
        dispatcher.dispatch(callback(clearData(AUTHOR_ID, AiClearCallback.Decision.CONFIRM), STRANGER_ID));

        ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(bot, times(1)).execute(captor.capture());
        assertEquals("Эту кнопку нажимал другой пользователь", captor.getValue().getText());

        verify(aiService, never()).clearChatHistory(any());
        verify(messageSender, never()).editMessage(any(), any(), any());
    }

    @Test
    void authorConfirmClearsHistoryAndEditsMessage() throws Exception {
        dispatcher.dispatch(callback(clearData(AUTHOR_ID, AiClearCallback.Decision.CONFIRM), AUTHOR_ID));

        verify(aiService).clearChatHistory(CHAT_ID);
        verify(messageSender).editMessage(CHAT_ID, MESSAGE_ID, "Память обнулена, забыто 7 сообщений");

        ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(bot, times(1)).execute(captor.capture());
        assertNull(captor.getValue().getText());
    }

    @Test
    void authorCancelKeepsHistory() throws Exception {
        dispatcher.dispatch(callback(clearData(AUTHOR_ID, AiClearCallback.Decision.CANCEL), AUTHOR_ID));

        verify(aiService, never()).clearChatHistory(any());
        verify(messageSender).editMessage(CHAT_ID, MESSAGE_ID, "Отменено");

        ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(bot, times(1)).execute(captor.capture());
        assertNull(captor.getValue().getText());
    }

    @Test
    void unknownCallbackAnswersWithToast() throws Exception {
        dispatcher.dispatch(callback("unknown", AUTHOR_ID));

        ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(bot).execute(captor.capture());
        assertEquals("Неизвестная кнопка", captor.getValue().getText());
        verify(aiService, never()).clearChatHistory(any());
    }

    private String clearData(Long authorId, AiClearCallback.Decision decision) {
        return new AiClearCallback(decision, authorId, FORGOTTEN_COUNT).toCallbackData();
    }

    private CallbackQuery callback(String data, Long fromId) {
        CallbackQuery callback = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        User from = mock(User.class);

        when(callback.getId()).thenReturn("callback-id");
        when(callback.getData()).thenReturn(data);
        lenient().when(callback.getFrom()).thenReturn(from);
        lenient().when(from.getId()).thenReturn(fromId);
        lenient().when(callback.getMessage()).thenReturn(message);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
        lenient().when(message.getMessageId()).thenReturn(MESSAGE_ID);

        return callback;
    }
}
