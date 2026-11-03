package org.example.AiChat;

import org.example.Settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.example.Settings.Settings.AI_PROMPT_EMOTIONS_HEADER;
import static org.example.Settings.Settings.AI_PROMPT_MEMORY_CORE_HEADER;
import static org.example.Settings.Settings.AI_PROMPT_PEOPLE_HEADER;
import static org.example.Settings.Settings.AI_PROMPT_TECHNICAL;
import static org.example.Settings.Settings.AI_PROMPT_TIME_ZONE;
import static org.example.Settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptComposerTest {

    private static final long CHAT_ID = 7L;
    private static final int PERSONA_ID = 3;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-03T22:20:00Z"), ZoneId.of("UTC"));

    @Mock
    private SettingsService settings;
    @Mock
    private PersonaService personaService;
    @Mock
    private EmotionService emotionService;
    @Mock
    private MemoryRepository memoryRepository;
    @Mock
    private AiChatRepository aiChatRepository;

    private PromptComposer composer;

    @BeforeEach
    void setUp() {
        composer = new PromptComposer(settings, personaService, emotionService, memoryRepository,
                new MemoryPromptBuilder(), aiChatRepository, CLOCK);
    }

    @Test
    void sectionsFollowFixedOrder() {
        withPersona();
        when(emotionService.bodyFor(PERSONA_ID, false)).thenReturn("- Гнев 8/10: орёшь");
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of());
        when(memoryRepository.findTopRegular(PERSONA_ID, PromptComposer.DEFAULT_MEMORY_PROMPT_SIZE))
                .thenReturn(List.of(new MemoryRecord(2, "хобби", "шахматы", 6, false, null, List.of())));
        when(aiChatRepository.findLastMessages(CHAT_ID, PromptComposer.PEOPLE_MESSAGES)).thenReturn(List.of(
                userMessage("привет", 111L, "@nick", "Ник")));

        List<PromptSection> order = composer.compose(CHAT_ID).sections().stream()
                .map(PromptComposer.Section::spec)
                .toList();

        assertEquals(List.of(
                PromptSection.TECHNICAL,
                PromptSection.PERSONA,
                PromptSection.EMOTIONS,
                PromptSection.MEMORY,
                PromptSection.PEOPLE,
                PromptSection.TIME,
                PromptSection.ANSWER_FORMAT), order);
    }

    @Test
    void emptySectionIsSkippedTogetherWithHeading() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());

        String prompt = composer.compose(CHAT_ID).text();

        assertFalse(prompt.contains("## Обиды"), prompt);
        assertFalse(prompt.contains("## Что ты помнишь"), prompt);
        assertFalse(prompt.contains("## Твоё состояние сейчас"), prompt);
        assertFalse(prompt.contains("## Кто в чате"), prompt);
        assertTrue(prompt.contains("## Сейчас"), prompt);
    }

    @Test
    void peopleListIsNewestFirstAndMarksCurrentAuthor() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(aiChatRepository.findLastMessages(CHAT_ID, PromptComposer.PEOPLE_MESSAGES)).thenReturn(List.of(
                userMessage("старое", 1L, "@old", "Старый"),
                userMessage("среднее", 2L, "@mid", "Средний"),
                userMessage("вопрос", 1L, "@old", "Старый")));

        String people = section(composer.compose(CHAT_ID), PromptSection.PEOPLE);

        assertTrue(people.indexOf("@old") < people.indexOf("@mid"), people);
        assertTrue(people.contains("@old (Старый) — сейчас пишет"), people);
        assertFalse(people.contains("@mid (Средний) — сейчас пишет"), people);
    }

    @Test
    void peopleListIsCappedAtTenLines() {
        List<AiMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 14; i++)
            messages.add(userMessage("текст", (long) i, "@user" + i, "Имя" + i));
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(aiChatRepository.findLastMessages(CHAT_ID, PromptComposer.PEOPLE_MESSAGES)).thenReturn(messages);

        String people = section(composer.compose(CHAT_ID), PromptSection.PEOPLE);

        assertEquals(11, people.lines().count(), people);
        assertTrue(people.contains("@user14"), people);
        assertFalse(people.contains("(Имя1)"), people);
    }

    @Test
    void personWithoutNameGoesWithoutEmptyBrackets() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(aiChatRepository.findLastMessages(CHAT_ID, PromptComposer.PEOPLE_MESSAGES)).thenReturn(List.of(
                userMessage("привет", 5L, "@nick", null)));

        String people = section(composer.compose(CHAT_ID), PromptSection.PEOPLE);

        assertTrue(people.contains("- @nick — сейчас пишет"), people);
        assertFalse(people.contains("()"), people);
    }

    @Test
    void timeUsesZoneFromSetting() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_TIME_ZONE)).thenReturn("Europe/Moscow");

        String time = section(composer.compose(CHAT_ID), PromptSection.TIME);

        assertTrue(time.contains("4 ноября 2026"), time);
        assertTrue(time.contains("01:20"), time);
        assertTrue(time.contains("Europe/Moscow"), time);
        assertTrue(time.contains("среда"), time);
    }

    @Test
    void invalidZoneFallsBackToMoscow() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_TIME_ZONE)).thenReturn("Марс/Олимп");

        String time = section(composer.compose(CHAT_ID), PromptSection.TIME);

        assertTrue(time.contains("Europe/Moscow"), time);
    }

    @Test
    void lengthPlaceholderIsFilledInTechnicalAndFormatSections() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());

        String prompt = composer.compose(CHAT_ID).text();

        assertFalse(prompt.contains("{maxLength}"), prompt);
        assertTrue(prompt.contains("4000"), prompt);
    }

    @Test
    void memoryHeadersComeFromSettings() {
        withPersona();
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of(
                new MemoryRecord(1, "семья", "родился", 10, true, LocalDate.of(1988, 4, 12), List.of())));
        when(settings.getSettingValue(AI_PROMPT_MEMORY_CORE_HEADER)).thenReturn("Моё ядро:");

        String memory = section(composer.compose(CHAT_ID), PromptSection.MEMORY);

        assertTrue(memory.startsWith("Моё ядро:"), memory);
        assertTrue(memory.contains("[семья] родился"), memory);
    }

    @Test
    void emotionHeaderComesFromSettings() {
        withPersona();
        when(emotionService.bodyFor(PERSONA_ID, false)).thenReturn("- Гнев 8/10: орёшь");
        when(settings.getSettingValue(AI_PROMPT_EMOTIONS_HEADER)).thenReturn("Настроение:");

        String emotions = section(composer.compose(CHAT_ID), PromptSection.EMOTIONS);

        assertTrue(emotions.startsWith("Настроение:"), emotions);
    }

    @Test
    void calmStateStaysOneLineWithoutHeader() {
        withPersona();
        when(emotionService.bodyFor(PERSONA_ID, false)).thenReturn(EmotionService.CALM_LINE);

        String emotions = section(composer.compose(CHAT_ID), PromptSection.EMOTIONS);

        assertEquals(EmotionService.CALM_LINE, emotions);
    }

    @Test
    void previewReadsEmotionsWithoutEnsuringRows() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.of(
                new Persona(PERSONA_ID, "Юра", "Ты Юра")));
        when(personaService.resolvePrompt(CHAT_ID)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Ты Юра", "persona:Юра")));
        when(emotionService.bodyFor(PERSONA_ID, true)).thenReturn("- Гнев 8/10: орёшь");

        composer.compose(CHAT_ID, true);

        verify(emotionService).bodyFor(PERSONA_ID, true);
        verify(emotionService, never()).bodyFor(PERSONA_ID, false);
    }

    @Test
    void blankSettingFallsBackToDefault() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_TECHNICAL)).thenReturn("   ");

        String prompt = composer.compose(CHAT_ID).text();

        assertTrue(prompt.contains("Ты участник этого чата"), prompt);
    }

    @Test
    void settingsChangeIsVisibleBetweenCalls() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_PEOPLE_HEADER))
                .thenReturn("Первые люди:")
                .thenReturn("Вторые люди:");
        when(aiChatRepository.findLastMessages(CHAT_ID, PromptComposer.PEOPLE_MESSAGES)).thenReturn(List.of(
                userMessage("привет", 1L, "@nick", "Ник")));

        String first = composer.compose(CHAT_ID).text();
        String second = composer.compose(CHAT_ID).text();

        assertTrue(first.contains("Первые люди:"), first);
        assertTrue(second.contains("Вторые люди:"), second);
    }

    private void withPersona() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.of(
                new Persona(PERSONA_ID, "Юра", "Ты Юра")));
        when(personaService.resolvePrompt(CHAT_ID)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Ты Юра", "persona:Юра")));
    }

    private static String section(PromptComposer.ComposedPrompt prompt, PromptSection spec) {
        return prompt.sections().stream()
                .filter(section -> section.spec() == spec)
                .map(PromptComposer.Section::body)
                .findFirst()
                .orElse("");
    }

    private static AiMessage userMessage(String text, Long userId, String nick, String name) {
        return new AiMessage(USER_ROLE, text, new MessageAuthor(userId, nick, name));
    }
}
