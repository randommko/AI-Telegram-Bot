package org.example.bot.aichat;

import org.example.bot.MessageSender;
import org.example.core.aichat.ChatPersona;
import org.example.core.aichat.MemoryRepository;
import org.example.core.aichat.MessageAuthor;
import org.example.core.aichat.MessageReactionRepository;
import org.example.core.aichat.MessageReactionRepository.ReactionMessage;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaService;
import org.example.core.aichat.PromptComposer;
import org.example.core.aichat.ReactionEmoji;
import org.example.core.aichat.ReactionEmojiRepository;
import org.example.core.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.example.core.settings.Settings.AI_REACTIONS_ENABLED;
import static org.example.core.settings.Settings.AI_REACTION_MODEL_USE_OWN;
import static org.example.core.settings.Settings.AI_REACTION_POOL_SIZE;
import static org.example.core.settings.Settings.AI_REACTION_PROMPT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionPassTest {
    private static final long CHAT = -1007777777777L;
    private static final int PERSONA = 5;
    private static final ChatPersona CHAT_PERSONA = new ChatPersona(CHAT, PERSONA);
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    @Mock
    private PersonaRepository personaRepository;
    @Mock
    private MessageReactionRepository messages;
    @Mock
    private ReactionEmojiRepository emojis;
    @Mock
    private PersonaService personaService;
    @Mock
    private PromptComposer composer;
    @Mock
    private PersonaGrudgeService grudges;
    @Mock
    private MemoryRepository memoryRepository;
    @Mock
    private LlmClient llmClient;
    @Mock
    private SettingsService settings;
    @Mock
    private MessageSender sender;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private ReactionPass pass;

    @BeforeEach
    void setUp() {
        pass = new ReactionPass(personaRepository, messages, emojis, personaService, composer, grudges,
                memoryRepository, llmClient, settings, sender, new ReactionDecisionParser(), clock);
        lenient().when(settings.getInt(eq(AI_REACTION_POOL_SIZE), eq(20))).thenReturn(20);
        lenient().when(settings.getSettingValue(AI_REACTION_PROMPT)).thenReturn(null);
        lenient().when(settings.getBoolean(eq(AI_REACTION_MODEL_USE_OWN), eq(false))).thenReturn(false);
    }

    @Test
    void dueRequiresSilenceOrFullPool() {
        assertFalse(ReactionPass.due(List.of(), 20, 30, NOW));
        assertTrue(ReactionPass.due(batchAt(NOW.minusSeconds(31), 2), 20, 30, NOW));
        assertFalse(ReactionPass.due(batchAt(NOW.minusSeconds(10), 2), 20, 30, NOW));
        assertTrue(ReactionPass.due(batchAt(NOW, 20), 20, 30, NOW));
    }

    @Test
    void disabledFunctionDoesNotTouchChats() {
        when(settings.getBoolean(eq(AI_REACTIONS_ENABLED), eq(false))).thenReturn(false);

        pass.scheduleDueChats();

        verifyNoInteractions(personaRepository);
    }

    @Test
    void firstRunInitializesWatermarkWithoutModelCall() {
        when(settings.getBoolean(eq(AI_REACTIONS_ENABLED), eq(false))).thenReturn(true);
        when(personaRepository.findAssignedChats()).thenReturn(List.of(CHAT_PERSONA));
        when(messages.readState(CHAT)).thenReturn(0L);
        when(messages.maxMessageId(CHAT)).thenReturn(500L);

        pass.scheduleDueChats();

        verify(messages).saveReadState(CHAT, 500L);
        verifyNoInteractions(llmClient);
    }

    @Test
    void sendsAtMostTwoReactionsAndAdvancesWatermark() {
        stubRun();
        when(llmClient.sendJsonRequestToAi(any(), any(), any(), eq(0.2f), eq(500)))
                .thenReturn("{\"reactions\":["
                        + "{\"message_id\":101,\"emoji\":\"🔥\",\"reason\":\"сильно\"},"
                        + "{\"message_id\":102,\"emoji\":\"🤡\",\"reason\":\"клоун\"}]}");
        when(sender.react(CHAT, 101, "🔥")).thenReturn(true);
        when(sender.react(CHAT, 102, "🤡")).thenReturn(true);

        pass.runPass(CHAT_PERSONA);

        verify(sender).react(CHAT, 101, "🔥");
        verify(sender).react(CHAT, 102, "🤡");
        verify(messages).saveReaction(CHAT, 101, "🔥", "сильно");
        verify(messages).saveReaction(CHAT, 102, "🤡", "клоун");
        verify(messages).saveReadState(CHAT, 2L);
    }

    @Test
    void emojiOutsideCatalogIsDropped() {
        stubRun();
        when(llmClient.sendJsonRequestToAi(any(), any(), any(), eq(0.2f), eq(500)))
                .thenReturn("{\"reactions\":[{\"message_id\":101,\"emoji\":\"👍\"}]}");

        pass.runPass(CHAT_PERSONA);

        verify(sender, never()).react(anyLong(), any(), any());
        verify(messages).saveReadState(CHAT, 2L);
    }

    @Test
    void modelFailureIsSilentAndDoesNotRepeatTheBatch() {
        stubRun();
        when(llmClient.sendJsonRequestToAi(any(), any(), any(), eq(0.2f), eq(500))).thenReturn(null);

        pass.runPass(CHAT_PERSONA);

        verifyNoInteractions(sender);
        verify(messages).saveReadState(CHAT, 2L);
    }

    @Test
    void emptyCatalogSkipsWithoutAdvancingWatermark() {
        when(messages.readState(CHAT)).thenReturn(100L);
        when(messages.findNewMessages(CHAT, 100, 20)).thenReturn(batchAt(NOW, 2));
        when(emojis.findUsableForEmotion()).thenReturn(List.of());

        pass.runPass(CHAT_PERSONA);

        verifyNoInteractions(llmClient, sender);
        verify(messages, never()).saveReadState(anyLong(), anyLong());
    }

    private void stubRun() {
        when(settings.getInt(eq(AI_REACTION_POOL_SIZE), eq(20))).thenReturn(20);
        when(messages.readState(CHAT)).thenReturn(100L);
        when(messages.findNewMessages(CHAT, 100, 20)).thenReturn(batchAt(NOW, 2));
        when(messages.countNewMessages(CHAT, 100)).thenReturn(2);
        when(emojis.findUsableForEmotion()).thenReturn(List.of(
                new ReactionEmoji("🔥", "сильно сказано", false, true),
                new ReactionEmoji("🤡", "клоунада", false, true)));
        when(personaService.resolvePrompt(CHAT))
                .thenReturn(Optional.of(new PersonaService.ResolvedPrompt("Ты Юра", "persona")));
        when(composer.composeForPlan(CHAT))
                .thenReturn(new PromptComposer.ComposedPrompt(List.of(), "контекст"));
        when(grudges.targeted(CHAT)).thenReturn(List.of());
        when(memoryRepository.findForPerson(eq(PERSONA), anyLong(), eq(3))).thenReturn(List.of());
    }

    private static List<ReactionMessage> batchAt(Instant time, int size) {
        List<ReactionMessage> batch = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            batch.add(new ReactionMessage(i, 100 + i, "текст " + i,
                    new MessageAuthor(1L, "@nick", "Ник"), time));
        }
        return batch;
    }
}
