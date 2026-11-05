package org.example;

import org.example.aichat.AiAddressParser;
import org.example.aichat.AiService;
import org.example.aichat.MessageAuthor;
import org.example.aichat.Persona;
import org.example.aichat.PersonaService;
import org.example.config.BotProperties;
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
import java.util.Optional;

import static org.example.settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final Long BLUE_CHAT_ID = -1001347887839L;
    private static final Long OTHER_CHAT_ID = -1007777777777L;
    private static final long BOT_ID = 42L;
    private static final List<String> ALIASES = List.of("юр", "юра", "юрец", "юрчик", "юрий");
    private static final Persona BLUE_PERSONA = new Persona(1, "Юра", "Ты Юра");
    private static final Persona OTHER_PERSONA = new Persona(2, "Макс", "Ты Макс");
    private static final List<String> OTHER_ALIASES = List.of("макс", "максим");

    @Mock
    private MessageSender messageSender;
    @Mock
    private AiService aiService;
    @Mock
    private PersonaService personaService;

    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        BotProperties properties = new BotProperties(BOT_ID + ":dummy", null, null, null, null);
        dispatcher = new CommandDispatcher(messageSender, aiService, new AiAddressParser(), personaService, properties);
    }

    @Test
    void addressInMyChatIsSentToAi() {
        when(personaService.personaForChat(BLUE_CHAT_ID)).thenReturn(Optional.of(BLUE_PERSONA));
        when(personaService.aliasesForChat(BLUE_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("Юра, как дела?", BLUE_CHAT_ID));

        verify(aiService).askAi(BLUE_CHAT_ID, "Юра, как дела?");
        assertSaved(BLUE_CHAT_ID, "Юра, как дела?", 111L, "@nick", "Ник");
    }

    @Test
    void addressInTheMiddleOfTheMessageIsSentToAi() {
        when(personaService.personaForChat(BLUE_CHAT_ID)).thenReturn(Optional.of(BLUE_PERSONA));
        when(personaService.aliasesForChat(BLUE_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("как дела, юра?", BLUE_CHAT_ID));

        verify(aiService).askAi(BLUE_CHAT_ID, "как дела, юра?");
    }

    @Test
    void addressWithoutQuestionAsksForAQuestion() {
        when(personaService.personaForChat(BLUE_CHAT_ID)).thenReturn(Optional.of(BLUE_PERSONA));
        when(personaService.aliasesForChat(BLUE_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("Юрий!", BLUE_CHAT_ID));

        verify(messageSender).sendMessage(BLUE_CHAT_ID, "Юра на связи, но вопроса нет. Напиши его после обращения.");
        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void addressWithoutQuestionNamesTheChatsPersona() {
        when(personaService.personaForChat(OTHER_CHAT_ID)).thenReturn(Optional.of(OTHER_PERSONA));
        when(personaService.aliasesForChat(OTHER_CHAT_ID)).thenReturn(OTHER_ALIASES);

        dispatcher.dispatch(update("Максим!", OTHER_CHAT_ID));

        verify(messageSender).sendMessage(OTHER_CHAT_ID,
                "Макс на связи, но вопроса нет. Напиши его после обращения.");
        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void addressInAnotherChatWithPersonaIsSentToAi() {
        when(personaService.personaForChat(OTHER_CHAT_ID)).thenReturn(Optional.of(OTHER_PERSONA));
        when(personaService.aliasesForChat(OTHER_CHAT_ID)).thenReturn(OTHER_ALIASES);

        dispatcher.dispatch(update("макс, как дела?", OTHER_CHAT_ID));

        verify(aiService).askAi(OTHER_CHAT_ID, "макс, как дела?");
        assertSaved(OTHER_CHAT_ID, "макс, как дела?", 111L, "@nick", "Ник");
    }

    @Test
    void foreignAliasInChatWithAnotherPersonaIsOnlySaved() {
        when(personaService.personaForChat(OTHER_CHAT_ID)).thenReturn(Optional.of(OTHER_PERSONA));
        when(personaService.aliasesForChat(OTHER_CHAT_ID)).thenReturn(OTHER_ALIASES);

        dispatcher.dispatch(update("юра, как дела?", OTHER_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        assertSaved(OTHER_CHAT_ID, "юра, как дела?", 111L, "@nick", "Ник");
    }

    @Test
    void plainTextInMyChatIsOnlySaved() {
        when(personaService.personaForChat(BLUE_CHAT_ID)).thenReturn(Optional.of(BLUE_PERSONA));
        when(personaService.aliasesForChat(BLUE_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(update("юридический вопрос по договору", BLUE_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        verify(messageSender, never()).sendMessage(anyLong(), anyString());
        assertSaved(BLUE_CHAT_ID, "юридический вопрос по договору", 111L, "@nick", "Ник");
    }

    @Test
    void chatWithoutPersonaHasNoNameTrigger() {
        when(personaService.personaForChat(BLUE_CHAT_ID)).thenReturn(Optional.empty());

        dispatcher.dispatch(update("юра, как дела?", BLUE_CHAT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        assertSaved(BLUE_CHAT_ID, "юра, как дела?", 111L, "@nick", "Ник");
    }

    @Test
    void authorWithoutUsernameIsSavedWithFirstName() {
        Message message = message("привет", BLUE_CHAT_ID);
        User author = mock(User.class);
        when(author.getFirstName()).thenReturn("Иван");
        when(message.getFrom()).thenReturn(author);
        Update update = mock(Update.class);
        when(update.getMessage()).thenReturn(message);

        dispatcher.dispatch(update);

        assertSaved(BLUE_CHAT_ID, "привет", 0L, null, "Иван");
    }

    @Test
    void replyToBotIsSentToAiWithoutAddress() {
        when(personaService.personaForChat(BLUE_CHAT_ID)).thenReturn(Optional.of(BLUE_PERSONA));

        dispatcher.dispatch(replyUpdate("ну что?", BLUE_CHAT_ID, BOT_ID));

        verify(aiService).askAi(BLUE_CHAT_ID, "ну что?");
    }

    @Test
    void replyToHumanIsNotSentToAi() {
        when(personaService.personaForChat(BLUE_CHAT_ID)).thenReturn(Optional.of(BLUE_PERSONA));
        when(personaService.aliasesForChat(BLUE_CHAT_ID)).thenReturn(ALIASES);

        dispatcher.dispatch(replyUpdate("ну что?", BLUE_CHAT_ID, 111L));

        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void replyToBotInAnotherChatWithPersonaIsSentToAi() {
        when(personaService.personaForChat(OTHER_CHAT_ID)).thenReturn(Optional.of(OTHER_PERSONA));

        dispatcher.dispatch(replyUpdate("ну что?", OTHER_CHAT_ID, BOT_ID));

        verify(aiService).askAi(OTHER_CHAT_ID, "ну что?");
    }

    @Test
    void replyToBotInChatWithoutPersonaIsOnlySaved() {
        when(personaService.personaForChat(OTHER_CHAT_ID)).thenReturn(Optional.empty());

        dispatcher.dispatch(replyUpdate("ну что?", OTHER_CHAT_ID, BOT_ID));

        verify(aiService, never()).askAi(anyLong(), anyString());
        assertSaved(OTHER_CHAT_ID, "ну что?", 111L, "@nick", "Ник");
    }

    @Test
    void slashCommandStillWorksAsBefore() {
        Update update = update("/юра как дела", BLUE_CHAT_ID);

        dispatcher.dispatch(update);

        verify(aiService).askAi(update.getMessage());
        verify(aiService, never()).askAi(anyLong(), anyString());
    }

    @Test
    void clearCommandAsksForConfirmationInsteadOfClearing() throws Exception {
        TelegramLongPollingBot bot = mock(TelegramLongPollingBot.class);
        when(messageSender.getBot()).thenReturn(bot);
        when(aiService.getChatHistorySize(BLUE_CHAT_ID)).thenReturn(7);

        dispatcher.dispatch(update("/clear", BLUE_CHAT_ID));

        verify(aiService, never()).clearChatHistory(anyLong());

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        assertTrue(captor.getValue().getText().contains("Обнулить память AI"), captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("7"), captor.getValue().getText());
    }

    @Test
    void botInfoListsClearCommand() {
        dispatcher.dispatch(update("/bot_info", BLUE_CHAT_ID));

        verify(messageSender).sendMessage(eq(BLUE_CHAT_ID), contains("/clear"));
    }

    /** Сохранённый текст не содержит префикса автора, а сам автор уходит отдельными полями. */
    private void assertSaved(Long chatId, String text, Long userId, String nick, String name) {
        ArgumentCaptor<MessageAuthor> captor = ArgumentCaptor.forClass(MessageAuthor.class);
        verify(aiService).saveMessage(eq(chatId), eq(USER_ROLE), eq(text), captor.capture());
        assertEquals(new MessageAuthor(userId, nick, name), captor.getValue());
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
