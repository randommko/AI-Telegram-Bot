package org.example.admin;

import org.example.core.aichat.Persona;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaSummary;
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

/** Привязка персоны к чату: очистка истории при смене, без сообщений в Telegram. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminChatController {
    private static final String JOIN_PREFIX = "В чат приходит ";

    private final PersonaRepository personas;
    private final AdminAccess access;

    public AdminChatController(PersonaRepository personas, AdminAccess access) {
        this.personas = personas;
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
        model.addAttribute("personas", editablePersonas());
        return "admin/chats";
    }

    /** Привязать к чату можно только персону, которую модератор вправе править. */
    private List<PersonaSummary> editablePersonas() {
        List<PersonaSummary> all = personas.findAllWithCounts(access.personaScope());
        Optional<List<Integer>> allowed = access.visiblePersonaIds();
        return allowed.map(ids -> all.stream().filter(persona -> ids.contains(persona.id())).toList())
                .orElse(all);
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

        Integer currentId = personas.findForChat(chatId).map(Persona::id).orElse(null);
        if (Objects.equals(currentId, personaId)) {
            redirect.addFlashAttribute("saved", "В чате " + chatId + " уже эта персона — ничего не изменилось");
            return "redirect:/admin/chats";
        }

        String joinText = personaId == null ? null : JOIN_PREFIX + personaName(personaId);
        personas.switchPersona(chatId, personaId, joinText);

        redirect.addFlashAttribute("saved", "Привязка сохранена, история чата обновлена");
        return "redirect:/admin/chats";
    }

    private String personaName(int personaId) {
        return personas.findAllWithCounts(PersonaRepository.Scope.owner()).stream()
                .filter(persona -> persona.id() == personaId)
                .map(PersonaSummary::name)
                .findFirst()
                .orElse("персона " + personaId);
    }
}
