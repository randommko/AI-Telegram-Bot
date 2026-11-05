package org.example.bot.aichat;

import org.example.core.aichat.ChatPersona;
import org.example.core.aichat.MemoryDraft;
import org.example.core.aichat.MemoryExtraction;
import org.example.core.aichat.MemoryRecord;

import org.example.core.chats.ChatsService;
import org.example.bot.MessageSender;
import org.example.core.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.example.core.settings.Settings.AI_MEMORY_NOTIFY_OWNER;
import static org.example.core.settings.Settings.OWNER_CHAT_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryUpdateNotifierTest {

    private static final ChatPersona CHAT = new ChatPersona(1L, 7);
    private static final long OWNER = 270459384L;

    @Mock
    private SettingsService settings;
    @Mock
    private MessageSender sender;
    @Mock
    private ChatsService chats;

    private MemoryUpdateNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new MemoryUpdateNotifier(settings, sender, chats);
    }

    @Test
    void reportsAllKindsOfChanges() {
        enabled();
        when(chats.getChatTitle(1L)).thenReturn("Синий чат");
        when(sender.sendLongMessage(eq(OWNER), anyString())).thenReturn(1);

        notifier.notifyOwner(CHAT, "Юра", existing(), applied("шахматы, турниры по выходным", 6),
                List.of(5L), 50);

        String text = sentText();
        assertTrue(text.contains("Память персоны «Юра»"), text);
        assertTrue(text.contains("Чат: «Синий чат» (1)"), text);
        assertTrue(text.contains("Разобрано сообщений: 50"), text);
        assertTrue(text.contains("+ [друзья, 6] Алексей — это Лёха"), text);
        assertTrue(text.contains("~ [хобби, 6] «шахматы» → «шахматы, турниры по выходным»"), text);
        assertTrue(text.contains("- [работа, 4] старая работа"), text);
        assertTrue(text.contains("Отброшено попыток удалить защищённую запись: 1"), text);
    }

    @Test
    void updateWithoutTextChangeShowsTextOnce() {
        enabled();
        when(chats.getChatTitle(1L)).thenReturn("Синий чат");
        when(sender.sendLongMessage(eq(OWNER), anyString())).thenReturn(1);

        notifier.notifyOwner(CHAT, "Юра", existing(), applied("шахматы", 9), List.of(), 50);

        String text = sentText();
        assertTrue(text.contains("~ [хобби, 9] шахматы"), text);
        assertFalse(text.contains("→"), "стрелка только при смене текста");
    }

    @Test
    void silentWhenNothingChanged() {
        notifier.notifyOwner(CHAT, "Юра", existing(), empty(), List.of(), 50);

        verifyNoInteractions(sender);
    }

    @Test
    void silentWhenDisabledInAdmin() {
        when(settings.getBoolean(AI_MEMORY_NOTIFY_OWNER, true)).thenReturn(false);

        notifier.notifyOwner(CHAT, "Юра", existing(), applied("шахматы", 6), List.of(), 50);

        verifyNoInteractions(sender);
    }

    @Test
    void silentWhenOwnerChatIsNotSet() {
        when(settings.getBoolean(AI_MEMORY_NOTIFY_OWNER, true)).thenReturn(true);
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn(" ");

        notifier.notifyOwner(CHAT, "Юра", existing(), applied("шахматы", 6), List.of(), 50);

        verifyNoInteractions(sender);
    }

    @Test
    void unknownChatFallsBackToItsId() {
        enabled();
        when(chats.getChatTitle(1L)).thenThrow(new IllegalStateException("чата нет"));
        when(sender.sendLongMessage(eq(OWNER), anyString())).thenReturn(1);

        notifier.notifyOwner(CHAT, "Юра", existing(), applied("шахматы", 6), List.of(), 50);

        assertTrue(sentText().contains("Чат: 1"), sentText());
    }

    @Test
    void sendFailureDoesNotThrow() {
        enabled();
        when(chats.getChatTitle(1L)).thenReturn("Синий чат");
        when(sender.sendLongMessage(anyLong(), anyString())).thenThrow(new IllegalStateException("телеграм недоступен"));

        assertDoesNotThrow(() -> notifier.notifyOwner(CHAT, "Юра", existing(),
                applied("шахматы", 6), List.of(), 50));

        verify(sender).sendLongMessage(eq(OWNER), anyString());
    }

    @Test
    void zeroSentPartsIsWarnedButNotThrown() {
        enabled();
        when(chats.getChatTitle(1L)).thenReturn("Синий чат");
        when(sender.sendLongMessage(eq(OWNER), anyString())).thenReturn(0);

        assertDoesNotThrow(() -> notifier.notifyOwner(CHAT, "Юра", existing(),
                applied("шахматы", 6), List.of(), 50));
    }

    private void enabled() {
        when(settings.getBoolean(AI_MEMORY_NOTIFY_OWNER, true)).thenReturn(true);
        when(settings.getSettingValue(OWNER_CHAT_ID)).thenReturn(String.valueOf(OWNER));
    }

    private String sentText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sender).sendLongMessage(eq(OWNER), text.capture());
        return text.getValue();
    }

    private static List<MemoryRecord> existing() {
        return List.of(
                new MemoryRecord(1, "семья", "родился 12.04.1988", 8, false, null, List.of()),
                new MemoryRecord(2, "хобби", "шахматы", 6, false, null, List.of()),
                new MemoryRecord(3, "работа", "старая работа", 4, false, null, List.of()));
    }

    private static MemoryExtraction applied(String updatedContent, int updatedImportance) {
        return new MemoryExtraction(
                List.of(new MemoryDraft(null, "друзья", "Алексей — это Лёха", 6, null, List.of())),
                List.of(new MemoryDraft(2L, "хобби", updatedContent, updatedImportance, null, List.of())),
                List.of(3L));
    }

    private static MemoryExtraction empty() {
        return new MemoryExtraction(List.of(), List.of(), List.of());
    }
}
