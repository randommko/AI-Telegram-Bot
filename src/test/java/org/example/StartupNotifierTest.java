package org.example;

import org.example.Config.BotProperties;
import org.example.Settings.SettingsService;
import org.junit.jupiter.api.Test;

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
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
    private final BotProperties properties = mock(BotProperties.class);

    @Test
    void addsAdminLinkWhenAdminIsEnabled() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("111222333");
        when(settings.getModel()).thenReturn("deepseek-flash");
        when(properties.hasAdmin()).thenReturn(true);
        when(properties.admin()).thenReturn(new BotProperties.Admin("secret", 8081, "127.0.0.1"));
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), argThat(text ->
                text.contains("Админка: http://127.0.0.1:8081/admin (через SSH-туннель)")));
    }

    @Test
    void omitsAdminLinkWhenAdminIsDisabled() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("111222333");
        when(settings.getModel()).thenReturn("deepseek-flash");
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), argThat(text -> !text.contains("Админка:")));
    }

    @Test
    void usesConfiguredAdminPort() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("111222333");
        when(settings.getModel()).thenReturn("deepseek-flash");
        when(properties.hasAdmin()).thenReturn(true);
        when(properties.admin()).thenReturn(new BotProperties.Admin("secret", 9090, "0.0.0.0"));
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), argThat(text ->
                text.contains("Админка: http://127.0.0.1:9090/admin")));
    }

    @Test
    void sendsStartupMessageToConfiguredChat() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("111222333");
        when(settings.getSettingValue(AI_ANSWER_TEMPERATURE)).thenReturn("1.8F");
        when(settings.getSettingValue(AI_MAX_TOKENS_ANSWER_QUESTION)).thenReturn("500");
        when(settings.getModel()).thenReturn("deepseek-flash");
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), argThat(text ->
                text.startsWith("Бот запущен")
                        && text.contains("Хост:")
                        && text.contains("Модель: deepseek-flash")
                        && text.contains("Температура: 1.8")
                        && text.contains("Максимум токенов: 500")));
    }

    @Test
    void takesModelFromSettings() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("111222333");
        when(settings.getModel()).thenReturn("deepseek-chat");
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), argThat(text -> text.contains("Модель: deepseek-chat")));
    }

    @Test
    void showsPlaceholdersWhenGenerationParametersAreMissing() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("111222333");
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), argThat(text ->
                text.contains("Температура: не задана")
                        && text.contains("Максимум токенов: не задан")));
    }

    @Test
    void trimsConfiguredChatId() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn(" 111222333 ");
        when(sender.sendMessage(eq(111222333L), anyString())).thenReturn(10);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verify(sender).sendMessage(eq(111222333L), anyString());
    }

    @Test
    void skipsWhenOwnerChatIsNotConfigured() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn(null);

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verifyNoInteractions(sender);
    }

    @Test
    void skipsWhenOwnerChatIsNotANumber() {
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn("не-id");

        new StartupNotifier(settings, sender, properties).notifyOwner();

        verifyNoInteractions(sender);
    }
}
