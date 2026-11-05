package org.example.bot;

import org.example.core.users.UsersService;
import org.example.core.chats.ChatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
public class UserChatManager {
    private static final Logger logger = LoggerFactory.getLogger(UserChatManager.class);
    private final UsersService usersService;
    private final ChatsService chatsService;

    public UserChatManager(UsersService usersService, ChatsService chatsService) {
        this.usersService = usersService;
        this.chatsService = chatsService;
    }

    // Обновление не чаще раза в сутки проверяется в SQL по updated_at, поэтому памяти процесса
    // для этого не нужно: после рестарта отметки сохраняются в БД.
    public void checkUser(User user) {
        if (!usersService.checkUser(user))
            usersService.addUser(user);
        else
            usersService.updateUser(user);
    }

    public void checkChat(Chat chat) {
        if (!chatsService.checkChat(chat.getId()))
            chatsService.addChat(chat);
        else
            chatsService.updateChat(chat);
    }
}
