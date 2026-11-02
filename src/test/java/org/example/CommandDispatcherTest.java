package org.example;

import org.example.AiChat.AiAddressParser;
import org.example.AiChat.AiService;
import org.example.CockSize.CockSizeGame;
import org.example.PidorGame.PidorGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.example.Settings.Settings.MY_CHAT_ID;
import static org.example.Settings.Settings.USER_ROLE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

    private static final Long OTHER_CHAT_ID = -1007777777777L;

    @Mock
    private MessageSender messageSender;
    @Mock
    private CockSizeGame cockSizeGame;
    @Mock
    private PidorGame pidorGame;
    @Mock
    private AiService aiService;

    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher(messageSender, cockSizeGame, pidorGame, aiService, new AiAddressParser());
    }

    @Test
    void addressInMyChatIsSentToAi() {
        dispatcher.dispatch(update("Юра, как дела?", MY_CHAT_ID));

        verify(aiService).askAi(MY_CHAT_ID, "как дела?");
        verify(aiService).saveMessage(eq(MY_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @nick: Юра, как дела?"));
    }

    @Test
    void addressWithoutQuestionAsksForAQuestion() {
        dispatcher.dispatch(update("Юрий!", MY_CHAT_ID));

        verify(messageSender).sendMessage(MY_CHAT_ID, "Юра на связи, но вопроса нет. Напиши его после обращения.");
        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void addressInAnotherChatIsNotSentToAi() {
        dispatcher.dispatch(update("юра, как дела?", OTHER_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        verify(aiService).saveMessage(eq(OTHER_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @nick: юра, как дела?"));
    }

    @Test
    void plainTextInMyChatIsOnlySaved() {
        dispatcher.dispatch(update("юридический вопрос по договору", MY_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        verify(messageSender, never()).sendMessage(anyLong(), anyString());
        verify(aiService).saveMessage(eq(MY_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @nick: юридический вопрос по договору"));
    }

    @Test
    void slashCommandStillWorksAsBefore() {
        Update update = update("/юра как дела", MY_CHAT_ID);

        dispatcher.dispatch(update);

        verify(aiService).askAi(update.getMessage());
        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    private Update update(String text, Long chatId) {
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        Update update = mock(Update.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn(text);
        // Ветка слэш-команды chatId не запрашивает, поэтому заглушка нужна не всем тестам.
        lenient().when(message.getChatId()).thenReturn(chatId);
        when(message.getChat()).thenReturn(chat);
        when(message.getFrom()).thenReturn(user);
        when(chat.getId()).thenReturn(chatId);
        when(user.getUserName()).thenReturn("nick");

        return update;
    }
}
