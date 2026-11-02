package org.example;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeyboardBuilderTest {

    private static final Long AUTHOR_ID = 1234567890123456789L;
    private static final int CALLBACK_DATA_LIMIT = 64;

    private final KeyboardBuilder keyboardBuilder = new KeyboardBuilder();

    @Test
    void aiClearQuestionAsksForConfirmationWithTwoButtons() throws Exception {
        MessageSender sender = mock(MessageSender.class);
        TelegramLongPollingBot bot = mock(TelegramLongPollingBot.class);
        when(sender.getBot()).thenReturn(bot);

        keyboardBuilder.sendAiClearQuestion(sender, -1007777777777L, AUTHOR_ID, 7);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        SendMessage question = captor.getValue();

        assertTrue(question.getText().contains("Обнулить память AI"), question.getText());
        assertTrue(question.getText().contains("7"), question.getText());

        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) question.getReplyMarkup();
        assertNotNull(keyboard);
        assertEquals(1, keyboard.getKeyboard().size());

        List<InlineKeyboardButton> row = keyboard.getKeyboard().get(0);
        assertEquals(2, row.size());
        assertEquals("Да", row.get(0).getText());
        assertEquals("Отмена", row.get(1).getText());

        for (InlineKeyboardButton button : row)
            assertTrue(button.getCallbackData().getBytes(StandardCharsets.UTF_8).length <= CALLBACK_DATA_LIMIT,
                    button.getCallbackData());
    }

    @Test
    void callbackDataOfMaxLengthIdIsParsedBack() {
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildAiClearKeyboard(AUTHOR_ID, 100);
        List<InlineKeyboardButton> row = keyboard.getKeyboard().get(0);

        Optional<AiClearCallback> confirm = AiClearCallback.parse(row.get(0).getCallbackData());
        Optional<AiClearCallback> cancel = AiClearCallback.parse(row.get(1).getCallbackData());

        assertTrue(confirm.isPresent());
        assertEquals(AiClearCallback.Decision.CONFIRM, confirm.get().decision());
        assertEquals(AUTHOR_ID, confirm.get().authorId());
        assertEquals(100, confirm.get().forgottenCount());

        assertTrue(cancel.isPresent());
        assertEquals(AiClearCallback.Decision.CANCEL, cancel.get().decision());
        assertEquals(AUTHOR_ID, cancel.get().authorId());
    }

    @Test
    void foreignCallbackDataIsNotRecognised() {
        assertTrue(AiClearCallback.parse(null).isEmpty());
        assertTrue(AiClearCallback.parse("aries_button_pressed").isEmpty());
        assertTrue(AiClearCallback.parse("avg_cock_size_button_pressed").isEmpty());
        assertTrue(AiClearCallback.parse("aiClear:maybe:1:2").isEmpty());
        assertTrue(AiClearCallback.parse("aiClear:yes:abc:2").isEmpty());
        assertTrue(AiClearCallback.parse("aiClear:yes:1:abc").isEmpty());
        assertTrue(AiClearCallback.parse("aiClear:yes:1").isEmpty());
        assertTrue(AiClearCallback.parse("aiClear:yes:1:2:3").isEmpty());
    }
}
