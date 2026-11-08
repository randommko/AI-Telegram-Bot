package org.example.bot;

import org.example.core.TextSplitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        TelegramClient client = mock(TelegramClient.class);
        doReturn(null).when(client).execute(any(EditMessageText.class));

        MessageSender sender = new MessageSender(client);

        assertTrue(sender.editMessage(-1007777777777L, 55, "Отменено"));

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(client).execute(captor.capture());
        EditMessageText edit = captor.getValue();

        assertEquals("Отменено", edit.getText());
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) edit.getReplyMarkup();
        assertNotNull(keyboard);
        assertTrue(keyboard.getKeyboard().isEmpty());
    }

    @Test
    void longMessageIsSplitIntoSeveralParts() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(1);
        when(client.execute(any(SendMessage.class))).thenReturn(sent);

        MessageSender sender = new MessageSender(client);

        assertEquals(3, sender.sendLongMessage(-1007777777777L, "a".repeat(9000)));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client, times(3)).execute(captor.capture());
        List<SendMessage> parts = captor.getAllValues();
        assertTrue(parts.stream().allMatch(part -> part.getText().length() <= TextSplitter.MAX_MESSAGE_LENGTH),
                parts.stream().map(part -> part.getText().length()).toList().toString());
    }

    @Test
    void failedPartDoesNotCancelTheRest() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(1);
        when(client.execute(any(SendMessage.class)))
                .thenReturn(sent)
                .thenThrow(new TelegramApiException("boom"))
                .thenReturn(sent);

        MessageSender sender = new MessageSender(client);

        assertEquals(2, sender.sendLongMessage(-1007777777777L, "a".repeat(9000)));
        verify(client, times(3)).execute(any(SendMessage.class));
    }

    @Test
    void replyGoesOnlyOnFirstPart() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(1);
        when(client.execute(any(SendMessage.class))).thenReturn(sent);

        MessageSender sender = new MessageSender(client);

        sender.sendLongMessage(-1007777777777L, "a".repeat(9000), 77);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client, times(3)).execute(captor.capture());
        List<SendMessage> parts = captor.getAllValues();
        assertEquals(Integer.valueOf(77), parts.get(0).getReplyToMessageId());
        assertNull(parts.get(1).getReplyToMessageId());
        assertNull(parts.get(2).getReplyToMessageId());
    }

    @Test
    void rejectedReplyIsSentWithoutQuote() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        Message sent = mock(Message.class);
        when(sent.getMessageId()).thenReturn(1);
        when(client.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("message to be replied not found"))
                .thenReturn(sent);

        MessageSender sender = new MessageSender(client);

        assertEquals(1, sender.sendMessage(-1007777777777L, "ответ", 77));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client, times(2)).execute(captor.capture());
        assertEquals(Integer.valueOf(77), captor.getAllValues().get(0).getReplyToMessageId());
        assertNull(captor.getAllValues().get(1).getReplyToMessageId());
        assertEquals("ответ", captor.getAllValues().get(1).getText());
    }
}
