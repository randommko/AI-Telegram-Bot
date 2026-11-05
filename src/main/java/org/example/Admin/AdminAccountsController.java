package org.example.Admin;

import org.example.AiChat.PersonaRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Раздел «Модераторы»: единственное место, где создаётся учётка и выдаётся чат.
 * Доступен только владельцу — это задано правилом в цепочке безопасности.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminAccountsController {
    private static final int MIN_PASSWORD = 6;

    private final AdminAccountsRepository accounts;
    private final ChatModeratorRepository moderators;
    private final PersonaRepository personas;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccess access;

    public AdminAccountsController(AdminAccountsRepository accounts, ChatModeratorRepository moderators,
                                   PersonaRepository personas, PasswordEncoder passwordEncoder,
                                   AdminAccess access) {
        this.accounts = accounts;
        this.moderators = moderators;
        this.personas = personas;
        this.passwordEncoder = passwordEncoder;
        this.access = access;
    }

    @GetMapping("/admin/moderators")
    String list(Model model) {
        model.addAttribute("title", "Модераторы");
        model.addAttribute("accounts", accounts.findAll());
        model.addAttribute("chats", personas.findAllChats(Optional.empty()));
        return "admin/moderators";
    }

    @PostMapping("/admin/moderators")
    String create(@RequestParam(required = false) String person,
                  @RequestParam(required = false) String login,
                  @RequestParam(required = false) String password,
                  RedirectAttributes redirect) {
        String cleanLogin = login == null ? "" : login.trim();
        String cleanPassword = password == null ? "" : password;

        if (AdminSecurityConfig.ADMIN_USERNAME.equalsIgnoreCase(cleanLogin)) {
            redirect.addFlashAttribute("error", "Логин admin закреплён за владельцем");
            return "redirect:/admin/moderators";
        }
        if (cleanLogin.isBlank()) {
            redirect.addFlashAttribute("error", "Логин не может быть пустым");
            return "redirect:/admin/moderators";
        }
        if (cleanPassword.length() < MIN_PASSWORD) {
            redirect.addFlashAttribute("error", "Пароль короче " + MIN_PASSWORD + " символов");
            return "redirect:/admin/moderators";
        }
        if (accounts.loginTaken(cleanLogin)) {
            redirect.addFlashAttribute("error", "Логин «" + cleanLogin + "» уже занят");
            return "redirect:/admin/moderators";
        }

        Optional<AdminAccountsRepository.AdminPerson> found = accounts.findPerson(person);
        if (found.isEmpty()) {
            redirect.addFlashAttribute("error", "Человек не найден: нужен @ник из чата или Telegram id");
            return "redirect:/admin/moderators";
        }
        if (accounts.personTaken(found.get().tgUserId())) {
            redirect.addFlashAttribute("error", "У этого человека уже есть учётка");
            return "redirect:/admin/moderators";
        }

        accounts.create(found.get().tgUserId(), cleanLogin,
                passwordEncoder.encode(cleanPassword));
        redirect.addFlashAttribute("saved", "Учётка «" + cleanLogin + "» создана, пароль передайте лично");
        return "redirect:/admin/moderators";
    }

    @PostMapping("/admin/moderators/{id}/delete")
    String delete(@PathVariable int id, RedirectAttributes redirect) {
        accounts.delete(id);
        redirect.addFlashAttribute("saved", "Учётка " + id + " удалена");
        return "redirect:/admin/moderators";
    }

    @PostMapping("/admin/moderators/{id}/password")
    String resetPassword(@PathVariable int id, @RequestParam(required = false) String password,
                         RedirectAttributes redirect) {
        if (password == null || password.length() < MIN_PASSWORD) {
            redirect.addFlashAttribute("error", "Пароль короче " + MIN_PASSWORD + " символов");
            return "redirect:/admin/moderators";
        }

        accounts.resetPassword(id, passwordEncoder.encode(password));
        redirect.addFlashAttribute("saved", "Пароль учётки " + id + " изменён");
        return "redirect:/admin/moderators";
    }

    @PostMapping("/admin/moderators/{id}/chats")
    String grant(@PathVariable int id, @RequestParam long chatId, RedirectAttributes redirect) {
        Optional<Long> tgUserId = accounts.tgUserIdOf(id);
        if (tgUserId.isEmpty()) {
            redirect.addFlashAttribute("error", "Учётка " + id + " не найдена");
            return "redirect:/admin/moderators";
        }

        boolean known = personas.findAllChats(Optional.empty()).stream()
                .anyMatch(chat -> chat.chatId() == chatId);
        if (!known) {
            redirect.addFlashAttribute("error", "Чат " + chatId + " бот ещё не видел");
            return "redirect:/admin/moderators";
        }

        moderators.grant(tgUserId.get(), chatId, access.authorId().orElse(null));
        redirect.addFlashAttribute("saved", "Чат " + chatId + " выдан");
        return "redirect:/admin/moderators";
    }

    @PostMapping("/admin/moderators/{id}/chats/{chatId}/delete")
    String revoke(@PathVariable int id, @PathVariable long chatId, RedirectAttributes redirect) {
        Optional<Long> tgUserId = accounts.tgUserIdOf(id);
        if (tgUserId.isEmpty()) {
            redirect.addFlashAttribute("error", "Учётка " + id + " не найдена");
            return "redirect:/admin/moderators";
        }

        moderators.revoke(tgUserId.get(), chatId);
        redirect.addFlashAttribute("saved", "Чат " + chatId + " отозван");
        return "redirect:/admin/moderators";
    }
}
