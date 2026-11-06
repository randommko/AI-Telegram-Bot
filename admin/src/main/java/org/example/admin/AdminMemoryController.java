package org.example.admin;

import org.example.core.aichat.MemoryRepository;
import org.example.core.aichat.PersonaDetails;
import org.example.core.aichat.PersonaRepository;
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

/**
 * Воспоминания персоны: формы, удаление и восстановление. Список живёт на вкладке
 * карточки персоны, поэтому персона всегда берётся из пути.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminMemoryController {

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

    /** Старый адрес списка: память переехала на вкладку карточки персоны. */
    @GetMapping("/admin/memories")
    String legacyList(@RequestParam(required = false) Integer personaId) {
        if (personaId != null && access.canEditPersona(personaId))
            return memoryTab(personaId);
        return "redirect:/admin/personas";
    }

    @GetMapping("/admin/personas/{personaId}/memories/new")
    String create(@PathVariable int personaId, @RequestParam(required = false) String category,
                  Model model, RedirectAttributes redirect) {
        Optional<PersonaDetails> persona = personas.findDetails(personaId);
        if (persona.isEmpty() || !access.canEditPersona(personaId))
            return denied(personaId, redirect);

        return form(model, MemoryForm.blank(personaId, category), List.of(), null, persona.get().name());
    }

    @GetMapping("/admin/personas/{personaId}/memories/{id}")
    String edit(@PathVariable int personaId, @PathVariable int id,
                Model model, RedirectAttributes redirect) {
        Optional<MemoryRepository.MemoryDetails> details = memories.findDetails(id);
        if (details.isEmpty() || details.get().personaId() != personaId)
            return notFound(personaId, id, redirect);
        if (!access.canEditPersona(personaId))
            return denied(personaId, redirect);

        return form(model, MemoryForm.of(details.get()), details.get().personIds(), null,
                personaName(personaId));
    }

    @PostMapping("/admin/personas/{personaId}/memories")
    String save(@PathVariable int personaId,
                @ModelAttribute MemoryForm form,
                @RequestParam(required = false, name = "people") List<Long> people,
                Model model, RedirectAttributes redirect) {
        if (form.personaId() == null || form.personaId() != personaId) {
            redirect.addFlashAttribute("error", "Воспоминание относится к другой персоне");
            return memoryTab(personaId);
        }

        AdminMemoryValidator.Result result = validator.validate(form, people, memories.categories());
        if (result.hasErrors())
            return form(model, form, people, result.errors(), personaName(personaId));

        MemoryRepository.MemoryDetails details = result.details();
        if (details.personaId() != personaId || !access.canEditPersona(personaId))
            return denied(personaId, redirect);

        if (form.id() == null) {
            memories.createManual(details.personaId(), details.category(), details.content(),
                    details.importance(), details.core(), details.eventDate(), details.personIds());
            redirect.addFlashAttribute("saved", "Воспоминание добавлено");
        } else {
            memories.updateManual(details.id(), details.category(), details.content(),
                    details.importance(), details.core(), details.eventDate(), details.personIds());
            redirect.addFlashAttribute("saved", "Воспоминание сохранено");
        }
        return memoryTab(personaId);
    }

    /** Удаление ядра требует отдельного подтверждения: анализ такую запись не восстановит. */
    @GetMapping("/admin/personas/{personaId}/memories/{id}/delete")
    String confirmDelete(@PathVariable int personaId, @PathVariable int id,
                         Model model, RedirectAttributes redirect) {
        Optional<MemoryRepository.MemoryDetails> details = memories.findDetails(id);
        if (details.isEmpty() || details.get().personaId() != personaId)
            return notFound(personaId, id, redirect);
        if (!access.canEditPersona(personaId))
            return denied(personaId, redirect);

        model.addAttribute("title", "Удаление воспоминания");
        model.addAttribute("memory", details.get());
        return "admin/memory-delete";
    }

    @PostMapping("/admin/personas/{personaId}/memories/{id}/delete")
    String delete(@PathVariable int personaId, @PathVariable int id,
                  @RequestParam(defaultValue = "false") boolean confirm,
                  RedirectAttributes redirect) {
        if (!confirm)
            return "redirect:/admin/personas/" + personaId + "/memories/" + id + "/delete";

        Optional<MemoryRepository.MemoryDetails> details = memories.findDetails(id);
        if (details.isEmpty() || details.get().personaId() != personaId)
            return notFound(personaId, id, redirect);
        if (!access.canEditPersona(personaId))
            return denied(personaId, redirect);

        memories.deleteManual(id);
        redirect.addFlashAttribute("saved",
                "Воспоминание " + id + " удалено — его можно вернуть в списке «удалённые»");
        return memoryTab(personaId);
    }

    /** Возврат удалённого воспоминания из списка «удалённые». */
    @PostMapping("/admin/personas/{personaId}/memories/{id}/restore")
    String restore(@PathVariable int personaId, @PathVariable int id, RedirectAttributes redirect) {
        Optional<MemoryRepository.MemoryDetails> details = memories.findAnyDetails(id);
        if (details.isEmpty() || details.get().personaId() != personaId)
            return notFound(personaId, id, redirect);
        if (!access.canEditPersona(personaId))
            return denied(personaId, redirect);

        memories.restoreManual(id);
        redirect.addFlashAttribute("saved", "Воспоминание возвращено");
        return memoryTab(personaId) + "&deleted=true";
    }

    private String form(Model model, MemoryForm form, List<Long> people, Map<String, String> errors,
                        String personaName) {
        model.addAttribute("title", form.id() == null ? "Новое воспоминание" : "Воспоминание " + form.id());
        model.addAttribute("form", form);
        model.addAttribute("personaName", personaName);
        model.addAttribute("people", people == null ? List.of() : people);
        model.addAttribute("errors", errors == null ? Map.of() : errors);
        model.addAttribute("categories", memories.categoriesWithTitles());
        model.addAttribute("allPeople", memories.allUsers());
        return "admin/memory-form";
    }

    private String personaName(int personaId) {
        return personas.findDetails(personaId).map(PersonaDetails::name).orElse("");
    }

    private String memoryTab(int personaId) {
        return "redirect:/admin/personas/" + personaId + "?tab=memories";
    }

    private String denied(int personaId, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Память этой персоны вам недоступна");
        return "redirect:/admin/personas/" + personaId;
    }

    private String notFound(int personaId, int id, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Воспоминание " + id + " не найдено");
        return memoryTab(personaId);
    }
}
