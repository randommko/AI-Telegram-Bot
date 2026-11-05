package org.example.Admin;

import org.example.AiChat.MemoryRepository;
import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PersonaSummary;
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

/** Воспоминания персоны: просмотр с фильтрами, ручная правка и ядро. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminMemoryController {
    private static final int PAGE_SIZE = 25;

    private final MemoryRepository memories;
    private final PersonaRepository personas;
    private final AdminMemoryValidator validator;
    private final AdminAccess access;

    public AdminMemoryController(MemoryRepository memories, PersonaRepository personas,
                                 AdminMemoryValidator validator, AdminAccess access) {
        this.memories = memories;
        this.personas = personas;
        this.validator = validator;
        this.access = access;
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

        if (form.id() == null) {
            memories.createManual(details.personaId(), details.category(), details.content(),
                    details.importance(), details.core(), details.eventDate(), details.personIds());
            redirect.addFlashAttribute("saved", "Воспоминание добавлено");
        } else {
            memories.updateManual(details.id(), details.category(), details.content(),
                    details.importance(), details.core(), details.eventDate(), details.personIds());
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

        memories.deleteManual(id);
        redirect.addFlashAttribute("saved", "Воспоминание " + id + " удалено");
        return "redirect:/admin/memories";
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

    private String form(Model model, MemoryForm form, List<Long> people, Map<String, String> errors) {
        model.addAttribute("title", form.id() == null ? "Новое воспоминание" : "Воспоминание " + form.id());
        model.addAttribute("form", form);
        model.addAttribute("people", people == null ? List.of() : people);
        model.addAttribute("errors", errors == null ? Map.of() : errors);
        model.addAttribute("categories", memories.categoriesWithTitles());
        model.addAttribute("allPeople", memories.allUsers());
        model.addAttribute("personas", editablePersonas());
        model.addAttribute("editable", true);
        return "admin/memory-form";
    }
}
