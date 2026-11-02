package org.example.Horoscope;

import org.example.MessageSender;
import org.example.Users.UsersService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HoroscopeServiceTest {

    private static final long CHAT_ID = -1001347887839L;
    private static final long USER_ID = 42L;

    private final UsersService usersService = mock(UsersService.class);
    private final MessageSender sender = mock(MessageSender.class);
    private final HoroscopeClient client = mock(HoroscopeClient.class);

    @Test
    void sendsHoroscopeOnceAndServesSecondClickFromCache() throws IOException {
        when(usersService.getUserNameByID(USER_ID)).thenReturn("Вася");
        when(client.fetch("aries", "today")).thenReturn("Спокойное начало недели.");

        HoroscopeService service = new HoroscopeService(usersService, sender, client);
        service.sendHoroscope(callback(), "aries", "today");
        service.sendHoroscope(callback(), "aries", "today");

        verify(client, times(1)).fetch("aries", "today");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).sendMessage(eq(CHAT_ID), message.capture());
        assertThat(message.getValue())
                .contains("ОВЕН")
                .contains("Вася")
                .contains("Спокойное начало недели.");
    }

    @Test
    void tellsUserWhenSourceIsUnavailable() throws IOException {
        when(usersService.getUserNameByID(USER_ID)).thenReturn("Вася");
        when(client.fetch("aries", "today")).thenThrow(new IOException("horo.mail.ru недоступен"));

        new HoroscopeService(usersService, sender, client).sendHoroscope(callback(), "aries", "today");

        verify(sender).sendMessage(CHAT_ID, "Гороскоп сейчас недоступен, попробуй позже.");
    }

    private CallbackQuery callback() {
        Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(CHAT_ID);

        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);

        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.getMessage()).thenReturn(message);
        when(callback.getFrom()).thenReturn(user);
        return callback;
    }
}
