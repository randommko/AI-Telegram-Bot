package org.example.AiChat;

import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static org.example.Settings.Settings.AI_CONTEXT;
import static org.example.Settings.Settings.AI_CONTEXT_FOR_MY_CHAT;
import static org.example.Settings.Settings.MY_CHAT_ID;

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

    /** Промпт персоны или настройки, если промпта персоны нет. */
    public record ResolvedPrompt(String text, String source) {
    }

    public Optional<Persona> personaForChat(long chatId) {
        return repository.findForChat(chatId);
    }

    public List<String> aliasesForChat(long chatId) {
        return repository.findAliasesForChat(chatId);
    }

    public Optional<ResolvedPrompt> resolvePrompt(long chatId) {
        Optional<Persona> persona = repository.findForChat(chatId);
        if (persona.isPresent() && hasText(persona.get().prompt()))
            return Optional.of(new ResolvedPrompt(persona.get().prompt(), "persona:" + persona.get().name()));

        String key = chatId == MY_CHAT_ID ? AI_CONTEXT_FOR_MY_CHAT : AI_CONTEXT;
        String configured = settings.getSettingValue(key);
        if (hasText(configured))
            return Optional.of(new ResolvedPrompt(configured, "setting:" + key));
        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
