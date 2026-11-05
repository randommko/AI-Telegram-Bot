package org.example.core.chats;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Chat;

@Service
public class ChatsService {
    private final ChatsRepository repo;

    public ChatsService(ChatsRepository repo) {
        this.repo = repo;
    }

    public boolean checkChat(Long chatID) {
        Chat chat = repo.getChatByID(chatID);
        return chat != null;
    }
    public void addChat(Chat chat) {
        repo.insertChatInDB(chat);
    }

    public void updateChat(Chat chat) {
        repo.updateChatInDB(chat);
    }

    public String getChatTitle(Long chatId) {
        return repo.getChatByID(chatId).getTitle();
    }
}
