package org.example.admin;

import org.example.core.aichat.Emotion;
import org.example.core.aichat.EmotionRepository;
import org.example.core.aichat.EmotionService;
import org.example.core.aichat.EmotionState;
import org.example.core.aichat.FieldNoteRepository;
import org.example.core.aichat.MemoryRepository;
import org.example.core.aichat.PersonaDetails;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PromptComposer;
import org.example.core.aichat.PromptDefaults;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    static final String TAB_MEMORIES = "memories";
    private static final int PAGE_SIZE = 25;
    private static final Set<String> EMOTION_TAB_FIELDS = Set.of(
            "aggressionThreshold", "hysteriaReplies", "hysteriaCooldownHours",
            "maxReplies", "replyDelayMs", "initiativeCooldownMessages", "initiativeEnabled");
    private static final String EMOTION_ERROR_PREFIX = "emotion.";
    private static final DateTimeFormatter CALCULATED_AT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId MOSCOW = ZoneId.of(PromptDefaults.TIME_ZONE);

    private final PersonaRepository personas;
    private final EmotionRepository emotions;
    private final EmotionService emotionStates;
    private final PromptComposer composer;
    private final FieldNoteRepository fieldNotes;
    private final MemoryRepository memories;
    private final AdminPersonaValidator validator;
    private final AdminAccess access;
    private final Clock clock;

    public AdminPersonaController(PersonaRepository personas, EmotionRepository emotions,
                                  EmotionService emotionStates, PromptComposer composer,
                                  FieldNoteRepository fieldNotes, MemoryRepository memories,
                                  AdminPersonaValidator validator, AdminAccess access, Clock clock) {
        this.personas = personas;
        this.emotions = emotions;
        this.emotionStates = emotionStates;
        this.composer = composer;
        this.fieldNotes = fieldNotes;
        this.memories = memories;
        this.validator = validator;
        this.access = access;
        this.clock = clock;
    }

    @GetMapping("/admin/personas")
    String list(Model model) {
        model.addAttribute("title", "Персоны");
        model.addAttribute("personas", personas.findAllWithCounts(access.personaScope()));
        model.addAttribute("owner", access.isOwner());
        return "admin/personas";
    }

    @GetMapping("/admin/personas/new")
    String create(@RequestParam(required = false) String tab, Model model) {
        List<Emotion> catalog = emotions.findCatalog();
        return form(model, PersonaForm.blank(catalog), null, catalog, tabId(tab), null);
    }

    @GetMapping("/admin/personas/{id}")
    String edit(@PathVariable int id,
                @RequestParam(required = false) String tab,
                @RequestParam(required = false) String category,
                @RequestParam(required = false) Long personId,
                @RequestParam(required = false) String q,
                @RequestParam(required = false) Boolean coreOnly,
                @RequestParam(required = false) Boolean deleted,
                @RequestParam(required = false) String sort,
                @RequestParam(required = false) Integer page,
                Model model, RedirectAttributes redirect) {
        Optional<PersonaDetails> details = personas.findDetails(id);
        if (details.isEmpty() || !access.canSeePersona(id)) {
            redirect.addFlashAttribute("error", "Персона " + id + " не найдена");
            return "redirect:/admin/personas";
        }

        String activeTab = TAB_MEMORIES.equals(tab) && access.canEditPersona(id)
                ? TAB_MEMORIES : tabId(tab);
        List<Emotion> catalog = emotions.findCatalog();
        String view = form(model, PersonaForm.of(details.get(), catalog), null, catalog, activeTab, id);

        if (TAB_MEMORIES.equals(activeTab))
            memoryTab(model, id, category, personId, q, coreOnly, deleted, sort, page);

        return view;
    }

    @PostMapping("/admin/personas")
    String save(@ModelAttribute PersonaForm form, @RequestParam(required = false) String tab,
                @RequestParam(required = false) Long chatId,
                Model model, RedirectAttributes redirect) {
        AdminPersonaValidator.Result result = validator.validate(form);
        if (result.hasErrors())
            return form(model, form, result.errors(), emotions.findCatalog(),
                    tabFor(result.errors(), tab), form.id());

        PersonaDetails details = result.details();
        if (details.id() == null) {
            if (!access.isOwner()) {
                if (chatId == null || !access.seesChat(chatId)) {
                    redirect.addFlashAttribute("error", "Чат для новой персоны не выдан");
                    return "redirect:/admin/personas";
                }
                personas.createInChat(details, chatId, access.authorId().orElse(null));
            } else {
                personas.create(details);
            }
            redirect.addFlashAttribute("saved", "Персона «" + details.name() + "» создана");
        } else {
            if (!access.canEditPersona(details.id())) {
                redirect.addFlashAttribute("error", "Эту персону можно только смотреть");
                return "redirect:/admin/personas/" + details.id();
            }
            personas.update(details);
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

        personas.delete(id);
        redirect.addFlashAttribute("saved", "Персона " + id + " удалена");
        return "redirect:/admin/personas";
    }

    /** Данные списка памяти для вкладки: персона берётся из пути, фильтр по персоне не нужен. */
    private void memoryTab(Model model, int personaId, String category, Long personId, String q,
                           Boolean coreOnly, Boolean deleted, String sort, Integer page) {
        int requestedPage = page == null ? 1 : page;
        MemoryRepository.MemoryFilter filter = new MemoryRepository.MemoryFilter(
                personaId, category, personId, q, Boolean.TRUE.equals(coreOnly), sort, requestedPage, PAGE_SIZE,
                Boolean.TRUE.equals(deleted), access.visiblePersonaIds().orElse(null));

        boolean filtered = (category != null && !category.isBlank()) || personId != null
                || (q != null && !q.isBlank()) || Boolean.TRUE.equals(coreOnly) || Boolean.TRUE.equals(deleted);

        model.addAttribute("memoryPage", memories.search(filter));
        model.addAttribute("memoryFilter", filter);
        model.addAttribute("memoryFilterActive", filtered);
        model.addAttribute("categories", memories.categoriesWithTitles());
        model.addAttribute("people", memories.allUsers());
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
        model.addAttribute("chats", access.isOwner() ? List.of() : personas.findAllChats(access.visibleChats()));
        if (form.id() != null)
            emotionState(model, form.id(), catalog);
        return "admin/persona-form";
    }

    /**
     * Блок текущего состояния: таблица и раздел промпта из одного чтения. Значения
     * читаются без добивки строк, поэтому открытие карточки ничего не пишет в базу.
     */
    private void emotionState(Model model, int personaId, List<Emotion> catalog) {
        model.addAttribute("emotionAt", ZonedDateTime.ofInstant(clock.instant(), MOSCOW)
                .format(CALCULATED_AT));

        EmotionState state = emotionStates.stateFor(personaId, true);
        model.addAttribute("emotionState", state);
        if (state == null)
            return;

        Map<String, EmotionState.Line> byCode = new LinkedHashMap<>();
        for (EmotionState.Line line : state.lines())
            byCode.put(line.code(), line);

        List<EmotionState.Line> rows = new ArrayList<>();
        for (Emotion emotion : catalog) {
            EmotionState.Line line = byCode.get(emotion.code());
            if (line != null)
                rows.add(line);
        }

        model.addAttribute("emotionRows", rows);
        // Без сохранённых строк «ровное состояние» было бы неправдой: бот их добивает и считает иначе.
        model.addAttribute("emotionSection", rows.isEmpty() ? null : composer.emotionsSection(state));
    }
}
