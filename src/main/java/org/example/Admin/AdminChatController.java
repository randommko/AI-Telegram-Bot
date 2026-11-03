package org.example.Admin;

import org.example.AiChat.Persona;
import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PersonaSummary;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.example.Settings.Settings.AI_PERSONA_JOIN_TEMPLATE;
import static org.example.Settings.Settings.AI_PERSONA_LEAVE_TEMPLATE;

/** Привязка персоны к чату: вход и выход персоны, очистка истории при смене. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminChatController {
    private static final Logger logger = LoggerFactory.getLogger(AdminChatController.class);
    private static final String DEFAULT_JOIN = "В чат приходит {name}";
    private static final String DEFAULT_LEAVE = "{name} покинул чат. Убрал: {moderator}";

    private final PersonaRepository personas;
    private final SettingsService settings;
    private final MessageSender sender;
    private final AdminAccess access;

    public AdminChatController(PersonaRepository personas, SettingsService settings, MessageSender sender,
                               AdminAccess access) {
        this.personas = personas;
        this.settings = settings;
        this.sender = sender;
        this.access = access;
    }

    /** Чаты с персоной идут первыми: администратор чаще приходит менять именно их. */
    public record ChatGroup(String title, List<PersonaRepository.ChatSummary> chats) {
    }

    @GetMapping("/admin/chats")
    String list(Model model) {
        Optional<List<Long>> scope = access.visibleChats();
        List<PersonaRepository.ChatSummary> chats = personas.findAllChats(scope);
        List<PersonaRepository.ChatSummary> withPersona = chats.stream()
                .filter(chat -> chat.personaId() != null).toList();
        List<PersonaRepository.ChatSummary> withoutPersona = chats.stream()
                .filter(chat -> chat.personaId() == null).toList();

        // Модератор видит один чат: заголовки групп ему ничего не объясняют.
        boolean owner = access.isOwner();
        List<ChatGroup> groups = new ArrayList<>();
        if (!withPersona.isEmpty())
            groups.add(new ChatGroup(owner ? "С персоной" : null, withPersona));
        if (!withoutPersona.isEmpty())
            groups.add(new ChatGroup(owner ? "Без персоны" : null, withoutPersona));

        model.addAttribute("title", "Чаты");
        model.addAttribute("groups", groups);
        model.addAttribute("personas", personas.findAllWithCounts(scope));
        return "admin/chats";
    }

    @PostMapping("/admin/chats/assign")
    String assign(@RequestParam long chatId,
                  @RequestParam(required = false) Integer personaId,
                  RedirectAttributes redirect) {
        if (!access.seesChat(chatId)) {
            redirect.addFlashAttribute("error", "Чат " + chatId + " вам не выдан");
            return "redirect:/admin/chats";
        }
        if (personaId != null && !access.canEditPersona(personaId)) {
            redirect.addFlashAttribute("error", "Эту персону вы не вправе привязывать");
            return "redirect:/admin/chats";
        }

        Optional<Persona> current = personas.findForChat(chatId);
        Integer currentId = current.map(Persona::id).orElse(null);

        if (Objects.equals(currentId, personaId)) {
            redirect.addFlashAttribute("saved", "В чате " + chatId + " уже эта персона — ничего не изменилось");
            return "redirect:/admin/chats";
        }

        String leaveText = current.map(this::leaveText).orElse(null);
        String joinText = personaId == null
                ? null
                : template(AI_PERSONA_JOIN_TEMPLATE, DEFAULT_JOIN, personaName(personaId));

        Actor actor = access.author().orElse(new Actor(null, AdminSecurityConfig.ADMIN_USERNAME, null, null));
        personas.switchPersona(chatId, personaId, joinText, actor);

        boolean telegramOk = true;
        if (leaveText != null)
            telegramOk = sender.sendMessage(chatId, leaveText) != null;
        if (joinText != null)
            telegramOk = sender.sendMessage(chatId, joinText) != null;

        if (telegramOk) {
            redirect.addFlashAttribute("saved", "Привязка сохранена, история чата обновлена");
        } else {
            logger.warn("Привязка персоны в чате {} сохранена, но Telegram не принял сообщение", chatId);
            redirect.addFlashAttribute("error",
                    "Привязка сохранена, но Telegram не принял сообщение — подробности в логе");
        }
        return "redirect:/admin/chats";
    }

    /** Сообщение о выходе называет того, кто снял персону; вход остаётся без автора. */
    private String leaveText(Persona persona) {
        String text = template(AI_PERSONA_LEAVE_TEMPLATE, DEFAULT_LEAVE, persona.name());
        String sign = access.author().map(Actor::sign).orElse(AdminSecurityConfig.ADMIN_USERNAME);
        return text.replace("{moderator}", sign);
    }

    private String template(String key, String fallback, String name) {
        String configured = settings.getSettingValue(key);
        String text = configured == null || configured.isBlank() ? fallback : configured;
        return text.replace("{name}", name);
    }

    private String personaName(int personaId) {
        return personas.findAllWithCounts(Optional.empty()).stream()
                .filter(persona -> persona.id() == personaId)
                .map(PersonaSummary::name)
                .findFirst()
                .orElse("персона " + personaId);
    }
}
