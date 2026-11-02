package org.example;

import org.example.Settings.SettingsService;
import org.junit.jupiter.api.Test;

import static org.example.Settings.Settings.OWNER_CHAT_ID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StartupNotifierTest {

    private final SettingsService settings = mock(SettingsService.class);
    private final MessageSender sender = mock(MessageSender.class);

    @Test
    void sendsStartupMessageToConfiguredChat() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("111222333");
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), argThat(text ->
                text.startsWith("Бот запущен") && text.contains("Хост:")));
    }

    @Test
    void trimsConfiguredChatId() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn(" 111222333 ");
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), anyString());
    }

    @Test
    void skipsWhenOwnerChatIsNotConfigured() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn(null);

        new StartupNotifier(settings, sender).notifyOwner();

        verifyNoInteractions(sender);
    }

    @Test
    void skipsWhenOwnerChatIsNotANumber() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("не-id");

        new StartupNotifier(settings, sender).notifyOwner();

        verifyNoInteractions(sender);
    }
}
