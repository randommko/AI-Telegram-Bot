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

import java.util.LinkedHashMap;
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
    private static final int HISTORY_LIMIT = 50;

    private final PersonaRepository personas;
    private final EmotionRepository emotions;
    private final FieldNoteRepository fieldNotes;
    private final AdminPersonaValidator validator;
    private final AdminAccess access;
    private final ChangeJournal journal;

    public AdminPersonaController(PersonaRepository personas, EmotionRepository emotions,
                                  FieldNoteRepository fieldNotes, AdminPersonaValidator validator,
                                  AdminAccess access, ChangeJournal journal) {
        this.personas = personas;
        this.emotions = emotions;
        this.fieldNotes = fieldNotes;
        this.validator = validator;
        this.access = access;
        this.journal = journal;
    }

    @GetMapping("/admin/personas")
    String list(Model model) {
        model.addAttribute("title", "Персоны");
        model.addAttribute("personas", personas.findAllWithCounts(access.visibleChats()));
        model.addAttribute("owner", access.isOwner());
        return "admin/personas";
    }

    @GetMapping("/admin/personas/new")
    String create(@RequestParam(required = false) String tab, Model model) {
        List<Emotion> catalog = emotions.findCatalog();
        return form(model, PersonaForm.blank(catalog), null, catalog, tabId(tab), null);
    }

    @GetMapping("/admin/personas/{id}")
    String edit(@PathVariable int id, @RequestParam(required = false) String tab,
                Model model, RedirectAttributes redirect) {
        Optional<PersonaDetails> details = personas.findDetails(id);
        if (details.isEmpty() || !access.canSeePersona(id)) {
            redirect.addFlashAttribute("error", "Персона " + id + " не найдена");
            return "redirect:/admin/personas";
        }
        List<Emotion> catalog = emotions.findCatalog();
        return form(model, PersonaForm.of(details.get(), catalog), null, catalog, tabId(tab), id);
    }

    @PostMapping("/admin/personas")
    String save(@ModelAttribute PersonaForm form, @RequestParam(required = false) String tab,
                @RequestParam(required = false) Long chatId,
                Model model, RedirectAttributes redirect) {
        AdminPersonaValidator.Result result = validator.validate(form);
        if (result.hasErrors())
            return form(model, form, result.errors(), emotions.findCatalog(),
                    tabFor(result.errors(), tab), form.id());

        Actor actor = actor();
        PersonaDetails details = result.details();
        if (details.id() == null) {
            if (!access.isOwner()) {
                if (chatId == null || !access.seesChat(chatId)) {
                    redirect.addFlashAttribute("error", "Чат для новой персоны не выдан");
                    return "redirect:/admin/personas";
                }
                personas.createInChat(details, chatId, actor);
            } else {
                personas.create(details, actor);
            }
            redirect.addFlashAttribute("saved", "Персона «" + details.name() + "» создана");
        } else {
            if (!access.canEditPersona(details.id())) {
                redirect.addFlashAttribute("error", "Эту персону можно только смотреть");
                return "redirect:/admin/personas/" + details.id();
            }
            personas.update(details, actor);
            redirect.addFlashAttribute("saved", "Персона «" + details.name() + "» сохранена");
        }
        return "redirect:/admin/personas";
    }

    @PostMapping("/admin/personas/{id}/delete")
    String delete(@PathVariable int id, RedirectAttributes redirect) {
        if (!access.isOwner()) {
            redirect.addFlashAttribute("error", "Удалять персон может только владелец");
            return "redirect:/admin/personas";
        }

        List<Long> chats = personas.chatsOf(id);
        if (!chats.isEmpty()) {
            redirect.addFlashAttribute("error",
                    "Персона привязана к чатам " + chats + " — сначала снимите привязку на странице «Чаты»");
            return "redirect:/admin/personas";
        }

        personas.delete(id, actor());
        redirect.addFlashAttribute("saved", "Персона " + id + " удалена");
        return "redirect:/admin/personas";
    }

    /** Возврат значений из журнала: обычная правка, поэтому след остаётся новой записью. */
    @PostMapping("/admin/personas/{id}/restore/{changeId}")
    String restore(@PathVariable int id, @PathVariable long changeId, RedirectAttributes redirect) {
        if (!access.canEditPersona(id)) {
            redirect.addFlashAttribute("error", "Эту персону можно только смотреть");
            return "redirect:/admin/personas/" + id;
        }

        Optional<ChangeJournal.Entry> entry = journal.find(changeId);
        if (entry.isEmpty() || !ChangeJournal.PERSONA.equals(entry.get().entity())
                || !Integer.valueOf(id).equals(entry.get().entityId())) {
            redirect.addFlashAttribute("error", "Запись журнала не относится к этой персоне");
            return "redirect:/admin/personas/" + id;
        }

        Optional<PersonaDetails> current = personas.findDetails(id);
        if (current.isEmpty()) {
            redirect.addFlashAttribute("error", "Персона " + id + " не найдена");
            return "redirect:/admin/personas";
        }

        personas.update(restored(current.get(), entry.get().before()), actor());
        redirect.addFlashAttribute("saved", "Возвращены значения из записи журнала");
        return "redirect:/admin/personas/" + id;
    }

    /** Подставляет старые значения из журнала в текущую карточку. */
    private PersonaDetails restored(PersonaDetails current, Map<String, Object> before) {
        List<String> aliases = strings(before.get("aliases"), current.aliases());
        Map<String, Integer> emotionValues = numbers(before.get("emotionValues"), current.emotionBaseValues());

        return new PersonaDetails(
                current.id(),
                text(before.get("name"), current.name()),
                text(before.get("prompt"), current.prompt()),
                aliases,
                emotionValues,
                number(before.get("aggressionThreshold"), current.aggressionThreshold()),
                number(before.get("hysteriaReplies"), current.hysteriaReplies()),
                number(before.get("hysteriaCooldownHours"), current.hysteriaCooldownHours()),
                number(before.get("maxReplies"), current.maxReplies()),
                number(before.get("replyDelayMs"), current.replyDelayMs()),
                bool(before.get("initiativeEnabled"), current.initiativeEnabled()),
                number(before.get("initiativeCooldownMessages"), current.initiativeCooldownMessages()));
    }

    private String text(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value, List<String> fallback) {
        return value instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> numbers(Object value, Map<String, Integer> fallback) {
        if (!(value instanceof Map<?, ?> map))
            return fallback;

        Map<String, Integer> numbers = new LinkedHashMap<>();
        map.forEach((key, raw) -> {
            if (raw instanceof Number number)
                numbers.put(String.valueOf(key), number.intValue());
        });
        return numbers.isEmpty() ? fallback : numbers;
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    private Actor actor() {
        return access.author().orElse(new Actor(null, AdminSecurityConfig.ADMIN_USERNAME, null, null));
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
                        List<Emotion> catalog, String tab, Integer id) {
        model.addAttribute("title", form.id() == null ? "Новая персона" : "Персона " + form.name());
        model.addAttribute("form", form);
        model.addAttribute("emotions", catalog);
        model.addAttribute("fieldNotes", fieldNotes.findAll());
        model.addAttribute("activeTab", tab);
        model.addAttribute("errors", errors == null ? Map.of() : errors);
        model.addAttribute("owner", access.isOwner());
        model.addAttribute("editable", form.id() == null || access.canEditPersona(form.id()));
        model.addAttribute("history", form.id() == null ? List.of() : journal.historyOfPersona(form.id(), HISTORY_LIMIT));
        model.addAttribute("chats", access.isOwner() ? List.of() : personas.findAllChats(access.visibleChats()));
        return "admin/persona-form";
    }
}
