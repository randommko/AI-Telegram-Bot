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
    private static final String DEFAULT_LEAVE = "{name} покинул чат";

    private final PersonaRepository personas;
    private final SettingsService settings;
    private final MessageSender sender;

    public AdminChatController(PersonaRepository personas, SettingsService settings, MessageSender sender) {
        this.personas = personas;
        this.settings = settings;
        this.sender = sender;
    }

    @GetMapping("/admin/chats")
    String list(Model model) {
        model.addAttribute("title", "Чаты");
        model.addAttribute("chats", personas.findAllChats());
        model.addAttribute("personas", personas.findAllWithCounts());
        return "admin/chats";
    }

    @PostMapping("/admin/chats/assign")
    String assign(@RequestParam long chatId,
                  @RequestParam(required = false) Integer personaId,
                  RedirectAttributes redirect) {
        Optional<Persona> current = personas.findForChat(chatId);
        Integer currentId = current.map(Persona::id).orElse(null);

        if (Objects.equals(currentId, personaId)) {
            redirect.addFlashAttribute("saved", "В чате " + chatId + " уже эта персона — ничего не изменилось");
            return "redirect:/admin/chats";
        }

        String leaveText = current
                .map(persona -> template(AI_PERSONA_LEAVE_TEMPLATE, DEFAULT_LEAVE, persona.name()))
                .orElse(null);
        String joinText = personaId == null
                ? null
                : template(AI_PERSONA_JOIN_TEMPLATE, DEFAULT_JOIN, personaName(personaId));

        personas.switchPersona(chatId, personaId, joinText);

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

    private String template(String key, String fallback, String name) {
        String configured = settings.getSettingValue(key);
        String text = configured == null || configured.isBlank() ? fallback : configured;
        return text.replace("{name}", name);
    }

    private String personaName(int personaId) {
        return personas.findAllWithCounts().stream()
                .filter(persona -> persona.id() == personaId)
                .map(PersonaSummary::name)
                .findFirst()
                .orElse("персона " + personaId);
    }
}
