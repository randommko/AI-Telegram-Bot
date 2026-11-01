package org.example.Users;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;


@Service
public class UsersService {
    private final UsersRepository repo;

    public UsersService(UsersRepository repo) {
        this.repo = repo;
    }

    public void addUser(User user) {
        repo.insertUserInDB(user);
    }

    public void updateUser(User user) {
        repo.updateUserInDB(user);
    }

    public boolean checkUser(User user) {
        return !(repo.getUserByID(user.getId()) == null);
    }

    public String getUserNameByID(Long userID) {
        if (repo.getUserByID(userID).getUserName() != null)
            return repo.getUserByID(userID).getUserName();
        return repo.getUserByID(userID).getFirstName();
    }
}
