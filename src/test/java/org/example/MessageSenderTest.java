package org.example;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
}
