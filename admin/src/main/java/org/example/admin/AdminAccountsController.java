package org.example.admin;

import org.example.core.aichat.PersonaRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ModeratorPersonaRepository personaGrants;
    private final PersonaRepository personas;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccess access;

    public AdminAccountsController(AdminAccountsRepository accounts, ChatModeratorRepository moderators,
                                   ModeratorPersonaRepository personaGrants, PersonaRepository personas,
                                   PasswordEncoder passwordEncoder, AdminAccess access) {
        this.accounts = accounts;
        this.moderators = moderators;
        this.personaGrants = personaGrants;
        this.personas = personas;
        this.passwordEncoder = passwordEncoder;
        this.access = access;
    }

    /** Учётка, её доступ к персонам и источник каждого доступа для галочек. */
    public record ModeratorRow(AdminAccountsRepository.AdminAccount account,
                               List<ModeratorPersonaRepository.PersonaAccessRow> personas,
                               Map<Integer, String> personaSources) {
    }

    @GetMapping("/admin/moderators")
    String list(Model model) {
        List<ModeratorRow> rows = accounts.findAll().stream()
                .map(account -> {
                    List<ModeratorPersonaRepository.PersonaAccessRow> access =
                            personaGrants.accessOf(account.tgUserId(), account.chatIds());
                    Map<Integer, String> sources = access.stream()
                            .collect(Collectors.toMap(
                                    ModeratorPersonaRepository.PersonaAccessRow::personaId,
                                    row -> row.source().name()));
                    return new ModeratorRow(account, access, sources);
                })
                .toList();

        List<PersonaRepository.ChatSummary> chats = personas.findAllChats(Optional.empty());
        Map<Long, String> chatTitles = new LinkedHashMap<>();
        for (PersonaRepository.ChatSummary chat : chats) {
            chatTitles.put(chat.chatId(), chat.title() == null || chat.title().isBlank()
                    ? String.valueOf(chat.chatId()) : chat.title());
        }

        model.addAttribute("title", "Модераторы");
        model.addAttribute("rows", rows);
        model.addAttribute("personas", personas.findAllWithCounts(PersonaRepository.Scope.owner()));
        model.addAttribute("chats", chats);
        model.addAttribute("chatTitles", chatTitles);
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

    /** Одна кнопка на строку: отмеченное выдаётся, снятое отзывается. */
    @PostMapping("/admin/moderators/{id}/access")
    String saveAccess(@PathVariable int id,
                      @RequestParam(required = false, name = "chatIds") List<Long> chatIds,
                      @RequestParam(required = false, name = "personaIds") List<Integer> personaIds,
                      RedirectAttributes redirect) {
        Optional<AdminAccountsRepository.AdminAccount> found = accounts.findById(id);
        if (found.isEmpty()) {
            redirect.addFlashAttribute("error", "Учётка " + id + " не найдена");
            return "redirect:/admin/moderators";
        }

        AdminAccountsRepository.AdminAccount account = found.get();
        Set<Long> requestedChats = new LinkedHashSet<>(chatIds == null ? List.of() : chatIds);
        Set<Integer> requestedPersonas = new LinkedHashSet<>(personaIds == null ? List.of() : personaIds);

        Set<Long> knownChats = personas.findAllChats(Optional.empty()).stream()
                .map(PersonaRepository.ChatSummary::chatId)
                .collect(Collectors.toSet());
        for (Long chatId : requestedChats) {
            if (!knownChats.contains(chatId)) {
                redirect.addFlashAttribute("error", "Чат " + chatId + " бот ещё не видел");
                return "redirect:/admin/moderators";
            }
        }

        List<ModeratorPersonaRepository.PersonaAccessRow> currentAccess =
                personaGrants.accessOf(account.tgUserId(), account.chatIds());
        Map<Integer, ModeratorPersonaRepository.Source> sources = currentAccess.stream()
                .collect(Collectors.toMap(
                        ModeratorPersonaRepository.PersonaAccessRow::personaId,
                        ModeratorPersonaRepository.PersonaAccessRow::source));
        Set<Integer> grantedPersonas = currentAccess.stream()
                .filter(row -> row.source() == ModeratorPersonaRepository.Source.GRANTED)
                .map(ModeratorPersonaRepository.PersonaAccessRow::personaId)
                .collect(Collectors.toSet());

        for (Integer personaId : requestedPersonas) {
            if (personas.findDetails(personaId).isEmpty()) {
                redirect.addFlashAttribute("error", "Персона " + personaId + " не найдена");
                return "redirect:/admin/moderators";
            }
            ModeratorPersonaRepository.Source source = sources.get(personaId);
            if (source == ModeratorPersonaRepository.Source.CREATED
                    || source == ModeratorPersonaRepository.Source.CHAT) {
                redirect.addFlashAttribute("error",
                        "Доступ к персоне " + personaId + " выдаётся не в этой форме");
                return "redirect:/admin/moderators";
            }
        }

        Long grantedBy = access.authorId().orElse(null);
        for (Long chatId : requestedChats) {
            if (!account.chatIds().contains(chatId))
                moderators.grant(account.tgUserId(), chatId, grantedBy);
        }
        for (Long chatId : account.chatIds()) {
            if (!requestedChats.contains(chatId))
                moderators.revoke(account.tgUserId(), chatId);
        }

        for (Integer personaId : requestedPersonas) {
            if (!grantedPersonas.contains(personaId))
                personaGrants.grant(account.tgUserId(), personaId, grantedBy);
        }
        for (Integer personaId : grantedPersonas) {
            if (!requestedPersonas.contains(personaId))
                personaGrants.revoke(account.tgUserId(), personaId);
        }

        redirect.addFlashAttribute("saved", "Доступы сохранены");
        return "redirect:/admin/moderators";
    }
}
