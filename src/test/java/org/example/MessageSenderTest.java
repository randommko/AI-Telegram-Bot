package org.example;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageSenderTest {

    @Test
    void editMessageRemovesInlineKeyboard() throws Exception {
        TelegramLongPollingBot bot = mock(TelegramLongPollingBot.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TelegramLongPollingBot> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bot);
        doReturn(null).when(bot).execute(any(EditMessageText.class));

        MessageSender sender = new MessageSender(provider);

        assertTrue(sender.editMessage(-1007777777777L, 55, "Отменено"));

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(bot).execute(captor.capture());
        EditMessageText edit = captor.getValue();

        assertEquals("Отменено", edit.getText());
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) edit.getReplyMarkup();
        assertNotNull(keyboard);
        assertTrue(keyboard.getKeyboard().isEmpty());
    }

    @Test
    void longMessageIsSplitIntoSeveralParts() throws Exception {
        TelegramLongPollingBot bot = mock(TelegramLongPollingBot.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TelegramLongPollingBot> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bot);
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(1);
        when(bot.execute(any(SendMessage.class))).thenReturn(sent);

        MessageSender sender = new MessageSender(provider);

        assertEquals(3, sender.sendLongMessage(-1007777777777L, "a".repeat(9000)));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, times(3)).execute(captor.capture());
        List<SendMessage> parts = captor.getAllValues();
        assertTrue(parts.stream().allMatch(part -> part.getText().length() <= TextSplitter.MAX_MESSAGE_LENGTH),
                parts.stream().map(part -> part.getText().length()).toList().toString());
    }

    @Test
    void failedPartDoesNotCancelTheRest() throws Exception {
        TelegramLongPollingBot bot = mock(TelegramLongPollingBot.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TelegramLongPollingBot> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bot);
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(1);
        when(bot.execute(any(SendMessage.class)))
                .thenReturn(sent)
                .thenThrow(new TelegramApiException("boom"))
                .thenReturn(sent);

        MessageSender sender = new MessageSender(provider);

        assertEquals(2, sender.sendLongMessage(-1007777777777L, "a".repeat(9000)));
        verify(bot, times(3)).execute(any(SendMessage.class));
    }
}
