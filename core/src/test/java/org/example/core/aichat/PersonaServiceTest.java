package org.example.core.aichat;

import org.example.core.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.example.core.settings.Settings.AI_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonaServiceTest {

    private static final long BLUE_CHAT_ID = -1001347887839L;

    @Mock
    private PersonaRepository repository;
    @Mock
    private SettingsService settings;

    private PersonaService service;

    @BeforeEach
    void setUp() {
        service = new PersonaService(repository, settings);
    }

    @Test
    void personaPromptWinsOverSettings() {
        when(repository.findForChat(1L)).thenReturn(Optional.of(new Persona(1, "Юра", "характер персоны")));

        PersonaService.ResolvedPrompt resolved = service.resolvePrompt(1L).orElseThrow();

        assertEquals("характер персоны", resolved.text());
        assertEquals("persona:Юра", resolved.source());
        verifyNoInteractions(settings);
    }

    @Test
    void emptyPersonaPromptFallsBackToSetting() {
        when(repository.findForChat(2L)).thenReturn(Optional.of(new Persona(2, "Юра", "   ")));
        when(settings.getSettingValue(AI_CONTEXT)).thenReturn("промпт из настройки");

        PersonaService.ResolvedPrompt resolved = service.resolvePrompt(2L).orElseThrow();

        assertEquals("промпт из настройки", resolved.text());
        assertEquals("setting:" + AI_CONTEXT, resolved.source());
    }

    @Test
    void blueChatWithoutPersonaUsesTheCommonPrompt() {
        when(repository.findForChat(BLUE_CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_CONTEXT)).thenReturn("общий промпт");

        PersonaService.ResolvedPrompt resolved = service.resolvePrompt(BLUE_CHAT_ID).orElseThrow();

        assertEquals("общий промпт", resolved.text());
        assertEquals("setting:" + AI_CONTEXT, resolved.source());
    }

    @Test
    void noPersonaAndNoSettingGivesNothing() {
        when(repository.findForChat(3L)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_CONTEXT)).thenReturn(null);

        assertTrue(service.resolvePrompt(3L).isEmpty());
    }

    @Test
    void aliasesComeFromTheChatsPersona() {
        when(repository.findAliasesForChat(4L)).thenReturn(java.util.List.of("юр", "юра"));

        assertEquals(java.util.List.of("юр", "юра"), service.aliasesForChat(4L));
    }
}
