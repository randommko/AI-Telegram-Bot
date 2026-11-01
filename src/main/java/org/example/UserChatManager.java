package org.example;

import org.example.Users.UsersService;
import org.example.Chats.ChatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserChatManager {
    private static final Logger logger = LoggerFactory.getLogger(UserChatManager.class);
    private final UsersService usersService;
    private final ChatsService chatsService;
    private final Map<Long, LocalDate> usersUpdateTime = new ConcurrentHashMap<>();
    private final Map<Long, LocalDate> chatsUpdateTime = new ConcurrentHashMap<>();

    public UserChatManager(UsersService usersService, ChatsService chatsService) {
        this.usersService = usersService;
        this.chatsService = chatsService;
    }

    public void checkUser(User user) {
        Long userId = user.getId();
        LocalDate today = LocalDate.now();

        if (!usersService.checkUser(user)) {
            usersService.addUser(user);
            usersUpdateTime.put(userId, today);
        } else if (!today.equals(usersUpdateTime.get(userId))) {
            usersService.updateUser(user);
            usersUpdateTime.put(userId, today);
        }
    }

    public void checkChat(Chat chat) {
        Long chatId = chat.getId();
        LocalDate today = LocalDate.now();

        if (!chatsService.checkChat(chatId)) {
            chatsService.addChat(chat);
            chatsUpdateTime.put(chatId, today);
        } else if (!today.equals(chatsUpdateTime.get(chatId))) {
            chatsService.updateChat(chat);
            chatsUpdateTime.put(chatId, today);
        }
    }
}
