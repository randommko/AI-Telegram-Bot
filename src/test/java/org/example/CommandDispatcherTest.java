package org.example;

import org.example.AiChat.AiAddressParser;
import org.example.AiChat.AiService;
import org.example.AiChat.PersonaService;
import org.example.CockSize.CockSizeGame;
import org.example.Config.BotProperties;
import org.example.PidorGame.PidorGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;

import static org.example.Settings.Settings.MY_CHAT_ID;
import static org.example.Settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

    private static final Long OTHER_CHAT_ID = -1007777777777L;
    private static final long BOT_ID = 42L;
    private static final List<String> ALIASES = List.of("юр", "юра", "юрец", "юрчик", "юрий");

    @Mock
    private MessageSender messageSender;
    @Mock
    private CockSizeGame cockSizeGame;
    @Mock
    private PidorGame pidorGame;
    @Mock
    private AiService aiService;
    @Mock
    private PersonaService personaService;

    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        BotProperties properties = new BotProperties(BOT_ID + ":dummy", null, null, null, null);
        dispatcher = new CommandDispatcher(messageSender, cockSizeGame, pidorGame, aiService,
                new AiAddressParser(), personaService, properties);
    }

    @Test
    void addressInMyChatIsSentToAi() {
        when(personaService.aliasesForChat(MY_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("Юра, как дела?", MY_CHAT_ID));

        verify(aiService).askAi(MY_CHAT_ID, "Юра, как дела?");
        verify(aiService).saveMessage(eq(MY_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @nick (Ник): Юра, как дела?"));
    }

    @Test
    void addressInTheMiddleOfTheMessageIsSentToAi() {
        when(personaService.aliasesForChat(MY_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("как дела, юра?", MY_CHAT_ID));

        verify(aiService).askAi(MY_CHAT_ID, "как дела, юра?");
    }

    @Test
    void addressWithoutQuestionAsksForAQuestion() {
        when(personaService.aliasesForChat(MY_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("Юрий!", MY_CHAT_ID));

        verify(messageSender).sendMessage(MY_CHAT_ID, "Юра на связи, но вопроса нет. Напиши его после обращения.");
        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void addressInAnotherChatIsNotSentToAi() {
        dispatcher.dispatch(update("юра, как дела?", OTHER_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        verify(aiService).saveMessage(eq(OTHER_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @nick (Ник): юра, как дела?"));
    }

    @Test
    void plainTextInMyChatIsOnlySaved() {
        when(personaService.aliasesForChat(MY_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("юридический вопрос по договору", MY_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        verify(messageSender, never()).sendMessage(anyLong(), anyString());
        verify(aiService).saveMessage(eq(MY_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @nick (Ник): юридический вопрос по договору"));
    }

    @Test
    void chatWithoutPersonaHasNoNameTrigger() {
        when(personaService.aliasesForChat(MY_CHAT_ID)).thenReturn(List.of());

        dispatcher.dispatch(update("юра, как дела?", MY_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        verify(aiService).saveMessage(eq(MY_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @nick (Ник): юра, как дела?"));
    }

    @Test
    void authorWithoutUsernameIsSavedWithFirstName() {
        when(personaService.aliasesForChat(MY_CHAT_ID)).thenReturn(List.of());
        Message message = message("привет", MY_CHAT_ID);
        User author = mock(User.class);
        when(author.getFirstName()).thenReturn("Иван");
        when(message.getFrom()).thenReturn(author);
        Update update = mock(Update.class);
        when(update.getMessage()).thenReturn(message);

        dispatcher.dispatch(update);

        verify(aiService).saveMessage(eq(MY_CHAT_ID), eq(USER_ROLE), eq("Сообщение от: @Иван: привет"));
    }

    @Test
    void replyToBotIsSentToAiWithoutAddress() {
        dispatcher.dispatch(replyUpdate("ну что?", MY_CHAT_ID, BOT_ID));

        verify(aiService).askAi(MY_CHAT_ID, "ну что?");
    }

    @Test
    void replyToHumanIsNotSentToAi() {
        when(personaService.aliasesForChat(MY_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(replyUpdate("ну что?", MY_CHAT_ID, 111L));

        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void replyToBotInAnotherChatIsNotSentToAi() {
        dispatcher.dispatch(replyUpdate("ну что?", OTHER_CHAT_ID, BOT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void slashCommandStillWorksAsBefore() {
        Update update = update("/юра как дела", MY_CHAT_ID);

        dispatcher.dispatch(update);

        verify(aiService).askAi(update.getMessage());
        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void clearCommandAsksForConfirmationInsteadOfClearing() throws Exception {
        TelegramLongPollingBot bot = mock(TelegramLongPollingBot.class);
        when(messageSender.getBot()).thenReturn(bot);
        when(aiService.getChatHistorySize(MY_CHAT_ID)).thenReturn(7);

        dispatcher.dispatch(update("/clear", MY_CHAT_ID));

        verify(aiService, never()).clearChatHistory(anyLong());

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        assertTrue(captor.getValue().getText().contains("Обнулить память AI"), captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("7"), captor.getValue().getText());
    }

    @Test
    void botInfoListsClearCommand() {
        dispatcher.dispatch(update("/bot_info", MY_CHAT_ID));

        verify(messageSender).sendMessage(eq(MY_CHAT_ID), contains("/clear"));
    }

    private Update update(String text, Long chatId) {
        Update update = mock(Update.class);
        Message message = message(text, chatId);
        when(update.getMessage()).thenReturn(message);
        return update;
    }

    private Update replyUpdate(String text, Long chatId, long replyAuthorId) {
        Update update = mock(Update.class);
        Message message = message(text, chatId);
        Message replied = mock(Message.class);
        User replyAuthor = mock(User.class);
        lenient().when(replied.getFrom()).thenReturn(replyAuthor);
        lenient().when(replyAuthor.getId()).thenReturn(replyAuthorId);
        lenient().when(message.getReplyToMessage()).thenReturn(replied);
        when(update.getMessage()).thenReturn(message);
        return update;
    }

    private Message message(String text, Long chatId) {
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(message.getText()).thenReturn(text);
        // Заглушки не нужны всем веткам, поэтому не строгие.
        lenient().when(message.getChatId()).thenReturn(chatId);
        lenient().when(message.getChat()).thenReturn(chat);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(message.getReplyToMessage()).thenReturn(null);
        lenient().when(chat.getId()).thenReturn(chatId);
        lenient().when(user.getUserName()).thenReturn("nick");
        lenient().when(user.getFirstName()).thenReturn("Ник");
        lenient().when(user.getId()).thenReturn(111L);

        return message;
    }
}
