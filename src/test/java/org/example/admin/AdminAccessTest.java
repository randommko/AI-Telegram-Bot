package org.example.admin;

import org.example.aichat.PersonaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Матрица прав: владелец, своя персона, общая персона, модератор без чатов. */
class AdminAccessTest {
    private final CurrentAdmin current = mock(CurrentAdmin.class);
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final ChatModeratorRepository moderators = mock(ChatModeratorRepository.class);
    private final AdminAccess access = new AdminAccess(current, personas, moderators);

    @Test
    void ownerSeesEverythingAndHasNoRestrictions() {
        when(current.isOwner()).thenReturn(true);

        assertEquals(Optional.empty(), access.visibleChats());
        assertEquals(Optional.empty(), access.visiblePersonaIds());
        assertTrue(access.seesChat(-100L));
        assertTrue(access.canSeePersona(1));
        assertTrue(access.canEditPersona(1));
    }

    @Test
    void moderatorSeesOnlyGrantedChats() {
        moderatorOf("vasya", List.of(-100L));

        assertEquals(Optional.of(List.of(-100L)), access.visibleChats());
        assertTrue(access.seesChat(-100L));
        assertFalse(access.seesChat(-200L));
    }

    @Test
    void moderatorEditsPersonaWhoseChatsAreAllHis() {
        moderatorOf("vasya", List.of(-100L));
        when(personas.permission(7, List.of(-100L)))
                .thenReturn(new PersonaRepository.PersonaPermission(1, 0));

        assertTrue(access.canSeePersona(7));
        assertTrue(access.canEditPersona(7));
        assertEquals(Optional.of(List.of(7)), access.visiblePersonaIds());
    }

    @Test
    void sharedPersonaIsReadOnly() {
        moderatorOf("vasya", List.of(-100L));
        when(personas.permission(7, List.of(-100L)))
                .thenReturn(new PersonaRepository.PersonaPermission(2, 1));

        assertTrue(access.canSeePersona(7));
        assertFalse(access.canEditPersona(7));
    }

    @Test
    void personaWithoutBoundChatsIsNotEditable() {
        moderatorOf("vasya", List.of(-100L));
        when(personas.permission(7, List.of(-100L)))
                .thenReturn(new PersonaRepository.PersonaPermission(0, 0));

        assertFalse(access.canSeePersona(7));
        assertFalse(access.canEditPersona(7));
    }

    @Test
    void moderatorWithoutChatsSeesNothing() {
        moderatorOf("vasya", List.of());

        assertEquals(Optional.of(List.of()), access.visibleChats());
        assertFalse(access.seesChat(-100L));
        assertFalse(access.canSeePersona(7));
        assertFalse(access.canEditPersona(7));
        assertEquals(Optional.of(List.of()), access.visiblePersonaIds());
    }

    private void moderatorOf(String login, List<Long> chats) {
        when(current.isOwner()).thenReturn(false);
        when(current.moderatorId()).thenReturn(Optional.of(777L));
        when(moderators.chatIdsOf(777L)).thenReturn(chats);
        when(personas.editablePersonaIds(chats)).thenReturn(chats.isEmpty() ? List.of() : List.of(7));
    }
}
