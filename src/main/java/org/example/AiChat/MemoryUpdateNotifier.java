package org.example.AiChat;

import org.example.Chats.ChatsService;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.example.Settings.Settings.AI_MEMORY_NOTIFY_OWNER;
import static org.example.Settings.Settings.OWNER_CHAT_ID;

/**
 * Отчёт владельцу об изменениях памяти после прогона анализа. Отправляется
 * после записи памяти: сбой доставки только логируется и ничего не откатывает.
 */
@Component
public class MemoryUpdateNotifier {
    private static final Logger logger = LoggerFactory.getLogger(MemoryUpdateNotifier.class);

    private final SettingsService settings;
    private final MessageSender sender;
    private final ChatsService chats;

    public MemoryUpdateNotifier(SettingsService settings, MessageSender sender, ChatsService chats) {
        this.settings = settings;
        this.sender = sender;
        this.chats = chats;
    }

    public void notifyOwner(ChatPersona chat, String personaName, List<MemoryRecord> existing,
                            MemoryExtraction applied, List<Long> rejected, int messages) {
        if (applied.add().isEmpty() && applied.update().isEmpty() && applied.delete().isEmpty())
            return;
        if (!settings.getBoolean(AI_MEMORY_NOTIFY_OWNER, true))
            return;

        Long owner = ownerChatId();
        if (owner == null)
            return;

        try {
            String text = report(chat, personaName, existing, applied, rejected, messages);
            if (sender.sendLongMessage(owner, text) == 0) {
                logger.warn("Отчёт об обновлении памяти персоны «{}» в чате {} не отправлен",
                        personaName, chat.chatId());
                return;
            }
            logger.info("Отчёт об обновлении памяти персоны «{}» в чате {} отправлен владельцу {}",
                    personaName, chat.chatId(), owner);
        } catch (Exception e) {
            logger.warn("Ошибка отправки отчёта об обновлении памяти в чате {}: {}",
                    chat.chatId(), String.valueOf(e));
        }
    }

    String report(ChatPersona chat, String personaName, List<MemoryRecord> existing,
                  MemoryExtraction applied, List<Long> rejected, int messages) {
        Map<Integer, MemoryRecord> before = new LinkedHashMap<>();
        for (MemoryRecord memory : existing)
            before.put(memory.id(), memory);

        StringBuilder text = new StringBuilder();
        if (personaName != null && !personaName.isBlank())
            text.append("Память персоны «").append(personaName).append("»\n");
        else
            text.append("Память персоны\n");
        text.append("Чат: ").append(chatLine(chat.chatId())).append('\n');
        text.append("Разобрано сообщений: ").append(messages);

        if (!applied.add().isEmpty()) {
            text.append("\n\nДобавлено (").append(applied.add().size()).append("):");
            for (MemoryDraft draft : applied.add())
                text.append("\n+ [").append(draft.category()).append(", ").append(draft.importance())
                        .append("] ").append(draft.content());
        }

        if (!applied.update().isEmpty()) {
            text.append("\n\nИзменено (").append(applied.update().size()).append("):");
            for (MemoryDraft draft : applied.update()) {
                MemoryRecord old = before.get(draft.id() == null ? null : draft.id().intValue());
                text.append("\n~ [").append(draft.category()).append(", ").append(draft.importance()).append("] ");
                if (old != null && !old.content().equals(draft.content()))
                    text.append("«").append(old.content()).append("» → «").append(draft.content()).append("»");
                else
                    text.append(draft.content());
            }
        }

        if (!applied.delete().isEmpty()) {
            text.append("\n\nУдалено (").append(applied.delete().size()).append("):");
            for (Long id : applied.delete()) {
                MemoryRecord old = id == null ? null : before.get(id.intValue());
                if (old == null)
                    text.append("\n- id ").append(id);
                else
                    text.append("\n- [").append(old.category()).append(", ").append(old.importance())
                            .append("] ").append(old.content());
            }
        }

        if (!rejected.isEmpty())
            text.append("\n\nОтброшено попыток удалить защищённую запись: ").append(rejected.size());

        return text.toString();
    }

    private String chatLine(long chatId) {
        String title = chatTitle(chatId);
        return title == null ? String.valueOf(chatId) : "«" + title + "» (" + chatId + ")";
    }

    private String chatTitle(long chatId) {
        try {
            String title = chats.getChatTitle(chatId);
            return title == null || title.isBlank() ? null : title;
        } catch (Exception e) {
            logger.debug("Название чата {} не прочитано: {}", chatId, String.valueOf(e));
            return null;
        }
    }

    private Long ownerChatId() {
        String value = settings.getSettingValue(OWNER_CHAT_ID);
        if (value == null || value.isBlank()) {
            logger.warn("Настройка {} не задана — уведомления о памяти отключены", OWNER_CHAT_ID);
            return null;
        }

        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Настройка {} содержит не число ('{}') — уведомления о памяти отключены",
                    OWNER_CHAT_ID, value);
            return null;
        }
    }
}
