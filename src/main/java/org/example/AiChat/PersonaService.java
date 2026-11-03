package org.example.AiChat;

import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static org.example.Settings.Settings.AI_CONTEXT;

/**
 * Персона и промпт чата. Читается из базы на каждый запрос: правки в админке
 * должны применяться без перезапуска процесса.
 */
@Service
public class PersonaService {
    private static final Logger logger = LoggerFactory.getLogger(PersonaService.class);

    private final PersonaRepository repository;
    private final SettingsService settings;

    public PersonaService(PersonaRepository repository, SettingsService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    /** Промпт персоны или общий промпт чата без персоны. */
    public record ResolvedPrompt(String text, String source) {
    }

    public Optional<Persona> personaForChat(long chatId) {
        return repository.findForChat(chatId);
    }

    public List<String> aliasesForChat(long chatId) {
        return repository.findAliasesForChat(chatId);
    }

    /**
     * Персона задаёт промпт чата; если персоны нет или её промпт пуст,
     * работает один общий промпт чата без персоны — без особого случая для «синего» чата.
     */
    public Optional<ResolvedPrompt> resolvePrompt(long chatId) {
        Optional<Persona> persona = repository.findForChat(chatId);
        if (persona.isPresent() && hasText(persona.get().prompt()))
            return Optional.of(new ResolvedPrompt(persona.get().prompt(), "persona:" + persona.get().name()));

        String configured = settings.getSettingValue(AI_CONTEXT);
        if (hasText(configured))
            return Optional.of(new ResolvedPrompt(configured, "setting:" + AI_CONTEXT));
        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
