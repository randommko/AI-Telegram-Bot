package org.example.Admin;

import org.example.AiChat.AiMemoryExtractor;
import org.example.AiChat.ChatPersona;
import org.example.AiChat.MemoryRepository;
import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PersonaSummary;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static org.example.Settings.Settings.AI_HISTORY_SIZE;

/** Воспоминания персоны: просмотр с фильтрами, ручная правка, ядро и прогон анализа. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminMemoryController {
    private static final Logger logger = LoggerFactory.getLogger(AdminMemoryController.class);
    private static final int PAGE_SIZE = 25;
    private static final int DEFAULT_HISTORY_SIZE = 100;
    private static final int HISTORY_LIMIT = 50;

    private final MemoryRepository memories;
    private final PersonaRepository personas;
    private final AdminMemoryValidator validator;
    private final AiMemoryExtractor extractor;
    private final SettingsService settings;
    private final AdminAccess access;
    private final ChangeJournal journal;

    public AdminMemoryController(MemoryRepository memories, PersonaRepository personas,
                                 AdminMemoryValidator validator, AiMemoryExtractor extractor,
                                 SettingsService settings, AdminAccess access, ChangeJournal journal) {
        this.memories = memories;
        this.personas = personas;
        this.validator = validator;
        this.extractor = extractor;
        this.settings = settings;
        this.access = access;
        this.journal = journal;
    }

    @GetMapping("/admin/memories")
    String list(@RequestParam(required = false) Integer personaId,
                @RequestParam(required = false) String category,
                @RequestParam(required = false) Long personId,
                @RequestParam(required = false) String q,
                @RequestParam(required = false) Boolean coreOnly,
                @RequestParam(required = false) String sort,
                @RequestParam(required = false) Integer page,
                Model model) {
        int requestedPage = page == null ? 1 : page;
        MemoryRepository.MemoryFilter filter = new MemoryRepository.MemoryFilter(
                personaId, category, personId, q, Boolean.TRUE.equals(coreOnly), sort, requestedPage, PAGE_SIZE,
                access.visiblePersonaIds().orElse(null));

        model.addAttribute("title", "Воспоминания");
        model.addAttribute("page", memories.search(filter));
        model.addAttribute("filter", filter);
        model.addAttribute("personas", editablePersonas());
        model.addAttribute("categories", memories.categoriesWithTitles());
        model.addAttribute("people", memories.allUsers());
        return "admin/memories";
    }

    @GetMapping("/admin/memories/new")
    String create(@RequestParam(required = false) Integer personaId,
                  @RequestParam(required = false) String category,
                  Model model) {
        Integer target = personaId != null ? personaId : firstPersonaId();
        if (target == null || !access.canEditPersona(target))
            return "redirect:/admin/memories";
        return form(model, MemoryForm.blank(target, category), List.of(), null);
    }

    @GetMapping("/admin/memories/{id}")
    String edit(@PathVariable int id, Model model, RedirectAttributes redirect) {
        Optional<MemoryRepository.MemoryDetails> details = memories.findDetails(id);
        if (details.isEmpty() || !access.canEditPersona(details.get().personaId())) {
            redirect.addFlashAttribute("error", "Воспоминание " + id + " не найдено");
            return "redirect:/admin/memories";
        }
        return form(model, MemoryForm.of(details.get()), details.get().personIds(), null);
    }

    @PostMapping("/admin/memories")
    String save(@ModelAttribute MemoryForm form,
                @RequestParam(required = false, name = "people") List<Long> people,
                Model model, RedirectAttributes redirect) {
        AdminMemoryValidator.Result result = validator.validate(form, people, memories.categories());
        if (result.hasErrors())
            return form(model, form, people, result.errors());

        MemoryRepository.MemoryDetails details = result.details();
        if (!access.canEditPersona(details.personaId())) {
            redirect.addFlashAttribute("error", "Память этой персоны вам недоступна");
            return "redirect:/admin/memories";
        }

        Actor actor = actor();
        if (form.id() == null) {
            memories.createManual(details.personaId(), details.category(), details.content(),
                    details.importance(), details.core(), details.eventDate(), details.personIds(), actor);
            redirect.addFlashAttribute("saved", "Воспоминание добавлено");
        } else {
            memories.updateManual(details.id(), details.category(), details.content(),
                    details.importance(), details.core(), details.eventDate(), details.personIds(), actor);
            redirect.addFlashAttribute("saved", "Воспоминание сохранено");
        }
        return "redirect:/admin/memories";
    }

    /** Удаление ядра требует отдельного подтверждения: анализ такую запись не восстановит. */
    @GetMapping("/admin/memories/{id}/delete")
    String confirmDelete(@PathVariable int id, Model model, RedirectAttributes redirect) {
        Optional<MemoryRepository.MemoryDetails> details = memories.findDetails(id);
        if (details.isEmpty() || !access.canEditPersona(details.get().personaId())) {
            redirect.addFlashAttribute("error", "Воспоминание " + id + " не найдено");
            return "redirect:/admin/memories";
        }
        model.addAttribute("title", "Удаление воспоминания");
        model.addAttribute("memory", details.get());
        return "admin/memory-delete";
    }

    @PostMapping("/admin/memories/{id}/delete")
    String delete(@PathVariable int id,
                  @RequestParam(defaultValue = "false") boolean confirm,
                  RedirectAttributes redirect) {
        if (!confirm) {
            redirect.addFlashAttribute("error", "Удаление нужно подтвердить на отдельной странице");
            return "redirect:/admin/memories/" + id + "/delete";
        }

        Optional<MemoryRepository.MemoryDetails> details = memories.findDetails(id);
        if (details.isEmpty() || !access.canEditPersona(details.get().personaId())) {
            redirect.addFlashAttribute("error", "Воспоминание " + id + " не найдено");
            return "redirect:/admin/memories";
        }

        memories.deleteManual(id, actor());
        redirect.addFlashAttribute("saved", "Воспоминание " + id + " удалено — его можно вернуть из журнала");
        return "redirect:/admin/memories";
    }

    /** Возврат удалённого воспоминания из журнала. */
    @PostMapping("/admin/memories/restore/{changeId}")
    String restore(@PathVariable long changeId, RedirectAttributes redirect) {
        Optional<ChangeJournal.Entry> entry = journal.find(changeId);
        if (entry.isEmpty() || !ChangeJournal.MEMORY.equals(entry.get().entity())
                || entry.get().entityId() == null || entry.get().personaId() == null) {
            redirect.addFlashAttribute("error", "Запись журнала не относится к воспоминанию");
            return "redirect:/admin/memories";
        }
        if (!access.canEditPersona(entry.get().personaId())) {
            redirect.addFlashAttribute("error", "Память этой персоны вам недоступна");
            return "redirect:/admin/memories";
        }

        memories.restoreManual(entry.get().entityId(), actor());
        redirect.addFlashAttribute("saved", "Воспоминание возвращено");
        return "redirect:/admin/memories";
    }

    /** Ручной прогон анализа: порог не учитывается, отметка разобранных сообщений соблюдается. */
    @PostMapping("/admin/memories/analyze")
    String analyze(@RequestParam int personaId, RedirectAttributes redirect) {
        if (!access.canEditPersona(personaId)) {
            redirect.addFlashAttribute("error", "Память этой персоны вам недоступна");
            return "redirect:/admin/memories";
        }

        List<Long> chats = personas.chatsOf(personaId);
        if (chats.isEmpty()) {
            redirect.addFlashAttribute("error", "У персоны нет привязанного чата — анализировать нечего");
            return "redirect:/admin/memories?personaId=" + personaId;
        }

        long chatId = chats.get(0);
        try {
            AiMemoryExtractor.AnalysisResult result = extractor.analyzeChat(
                    new ChatPersona(chatId, personaId),
                    settings.getInt(AI_HISTORY_SIZE, DEFAULT_HISTORY_SIZE));

            if (result.messages() == 0) {
                redirect.addFlashAttribute("saved", "Новых сообщений нет — анализировать нечего");
            } else {
                redirect.addFlashAttribute("saved", "Разобрано сообщений: " + result.messages()
                        + ", добавлено: " + result.added()
                        + ", обновлено: " + result.updated()
                        + ", удалено: " + result.deleted()
                        + ", отброшено удалений защищённых записей: " + result.rejectedDeletes());
            }
        } catch (Exception e) {
            logger.error("Ручной прогон анализа для персоны {} завершился ошибкой: ", personaId, e);
            redirect.addFlashAttribute("error", "Анализ не выполнен: " + e.getMessage());
        }
        return "redirect:/admin/memories?personaId=" + personaId;
    }

    /** Персоны, память которых видна: у модератора — только те, что он вправе править. */
    private List<PersonaSummary> editablePersonas() {
        List<PersonaSummary> all = personas.findAllWithCounts(access.visibleChats());
        Optional<List<Integer>> allowed = access.visiblePersonaIds();
        return allowed.map(ids -> all.stream().filter(persona -> ids.contains(persona.id())).toList())
                .orElse(all);
    }

    private Integer firstPersonaId() {
        return editablePersonas().stream().map(PersonaSummary::id).findFirst().orElse(null);
    }

    private Actor actor() {
        return access.author().orElse(new Actor(null, AdminSecurityConfig.ADMIN_USERNAME, null, null));
    }

    private String form(Model model, MemoryForm form, List<Long> people, Map<String, String> errors) {
        model.addAttribute("title", form.id() == null ? "Новое воспоминание" : "Воспоминание " + form.id());
        model.addAttribute("form", form);
        model.addAttribute("people", people == null ? List.of() : people);
        model.addAttribute("errors", errors == null ? Map.of() : errors);
        model.addAttribute("categories", memories.categoriesWithTitles());
        model.addAttribute("allPeople", memories.allUsers());
        model.addAttribute("personas", editablePersonas());
        model.addAttribute("editable", true);
        model.addAttribute("history", form.id() == null ? List.of() : journal.historyOfMemory(form.id(), HISTORY_LIMIT));
        return "admin/memory-form";
    }
}
