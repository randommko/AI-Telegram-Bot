package org.example.Admin;

import org.example.AiChat.Emotion;
import org.example.AiChat.EmotionRepository;
import org.example.AiChat.FieldNoteRepository;
import org.example.AiChat.PersonaDetails;
import org.example.AiChat.PersonaRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Карточки персон: вкладки «Общее» и «Эмоции», значения эмоций из справочника. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminPersonaController {
    static final String TAB_GENERAL = "general";
    static final String TAB_EMOTIONS = "emotions";
    private static final Set<String> EMOTION_TAB_FIELDS = Set.of(
            "aggressionThreshold", "hysteriaReplies", "hysteriaCooldownHours",
            "maxReplies", "replyDelayMs", "initiativeCooldownMessages", "initiativeEnabled");
    private static final String EMOTION_ERROR_PREFIX = "emotion.";

    private final PersonaRepository personas;
    private final EmotionRepository emotions;
    private final FieldNoteRepository fieldNotes;
    private final AdminPersonaValidator validator;

    public AdminPersonaController(PersonaRepository personas, EmotionRepository emotions,
                                  FieldNoteRepository fieldNotes, AdminPersonaValidator validator) {
        this.personas = personas;
        this.emotions = emotions;
        this.fieldNotes = fieldNotes;
        this.validator = validator;
    }

    @GetMapping("/admin/personas")
    String list(Model model) {
        model.addAttribute("title", "Персоны");
        model.addAttribute("personas", personas.findAllWithCounts());
        return "admin/personas";
    }

    @GetMapping("/admin/personas/new")
    String create(@RequestParam(required = false) String tab, Model model) {
        List<Emotion> catalog = emotions.findCatalog();
        return form(model, PersonaForm.blank(catalog), null, catalog, tabId(tab));
    }

    @GetMapping("/admin/personas/{id}")
    String edit(@PathVariable int id, @RequestParam(required = false) String tab,
                Model model, RedirectAttributes redirect) {
        Optional<PersonaDetails> details = personas.findDetails(id);
        if (details.isEmpty()) {
            redirect.addFlashAttribute("error", "Персона " + id + " не найдена");
            return "redirect:/admin/personas";
        }
        List<Emotion> catalog = emotions.findCatalog();
        return form(model, PersonaForm.of(details.get(), catalog), null, catalog, tabId(tab));
    }

    @PostMapping("/admin/personas")
    String save(@ModelAttribute PersonaForm form, @RequestParam(required = false) String tab,
                Model model, RedirectAttributes redirect) {
        AdminPersonaValidator.Result result = validator.validate(form);
        if (result.hasErrors())
            return form(model, form, result.errors(), emotions.findCatalog(), tabFor(result.errors(), tab));

        PersonaDetails details = result.details();
        if (details.id() == null) {
            personas.create(details);
            redirect.addFlashAttribute("saved", "Персона «" + details.name() + "» создана");
        } else {
            personas.update(details);
            redirect.addFlashAttribute("saved", "Персона «" + details.name() + "» сохранена");
        }
        return "redirect:/admin/personas";
    }

    @PostMapping("/admin/personas/{id}/delete")
    String delete(@PathVariable int id, RedirectAttributes redirect) {
        List<Long> chats = personas.chatsOf(id);
        if (!chats.isEmpty()) {
            redirect.addFlashAttribute("error",
                    "Персона привязана к чатам " + chats + " — сначала снимите привязку на странице «Чаты»");
            return "redirect:/admin/personas";
        }

        personas.delete(id);
        redirect.addFlashAttribute("saved", "Персона " + id + " удалена");
        return "redirect:/admin/personas";
    }

    /** Вкладка ошибки: первая ошибка решает, какую вкладку показать. */
    private String tabFor(Map<String, String> errors, String requested) {
        for (String key : errors.keySet()) {
            if (key.startsWith(EMOTION_ERROR_PREFIX) || EMOTION_TAB_FIELDS.contains(key))
                return TAB_EMOTIONS;
            return TAB_GENERAL;
        }
        return tabId(requested);
    }

    private String tabId(String tab) {
        return TAB_EMOTIONS.equals(tab) ? TAB_EMOTIONS : TAB_GENERAL;
    }

    private String form(Model model, PersonaForm form, Map<String, String> errors,
                        List<Emotion> catalog, String tab) {
        model.addAttribute("title", form.id() == null ? "Новая персона" : "Персона " + form.name());
        model.addAttribute("form", form);
        model.addAttribute("emotions", catalog);
        model.addAttribute("fieldNotes", fieldNotes.findAll());
        model.addAttribute("activeTab", tab);
        model.addAttribute("errors", errors == null ? Map.of() : errors);
        return "admin/persona-form";
    }
}
