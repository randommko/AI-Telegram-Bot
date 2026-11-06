package org.example.admin;

import org.example.core.aichat.PersonaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Матрица прав: владелец, персона своего чата, созданная, выданная и чужая. */
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
        assertEquals(PersonaRepository.Scope.owner(), access.personaScope());
    }

    @Test
    void moderatorSeesOnlyGrantedChats() {
        moderatorOf(List.of(-100L));

        assertEquals(Optional.of(List.of(-100L)), access.visibleChats());
        assertTrue(access.seesChat(-100L));
        assertFalse(access.seesChat(-200L));
    }

    @Test
    void chatGivesViewButNotEdit() {
        moderatorOf(List.of(-100L));
        when(personas.personaAccess(7, 777L, List.of(-100L)))
                .thenReturn(new PersonaRepository.PersonaAccess(false, false, true));

        assertTrue(access.canSeePersona(7));
        assertFalse(access.canEditPersona(7));
    }

    @Test
    void createdPersonaIsEditable() {
        moderatorOf(List.of(-100L));
        when(personas.personaAccess(7, 777L, List.of(-100L)))
                .thenReturn(new PersonaRepository.PersonaAccess(true, false, true));
        when(personas.editablePersonaIds(777L)).thenReturn(List.of(7));

        assertTrue(access.canSeePersona(7));
        assertTrue(access.canEditPersona(7));
        assertEquals(Optional.of(List.of(7)), access.visiblePersonaIds());
    }

    @Test
    void grantedPersonaIsEditableWithoutAnyChat() {
        moderatorOf(List.of(-100L));
        when(personas.personaAccess(9, 777L, List.of(-100L)))
                .thenReturn(new PersonaRepository.PersonaAccess(false, true, false));

        assertTrue(access.canSeePersona(9));
        assertTrue(access.canEditPersona(9));
    }

    @Test
    void foreignPersonaIsHidden() {
        moderatorOf(List.of(-100L));
        when(personas.personaAccess(7, 777L, List.of(-100L)))
                .thenReturn(new PersonaRepository.PersonaAccess(false, false, false));

        assertFalse(access.canSeePersona(7));
        assertFalse(access.canEditPersona(7));
    }

    @Test
    void moderatorWithoutChatsSeesNothing() {
        moderatorOf(List.of());
        when(personas.personaAccess(7, 777L, List.of()))
                .thenReturn(new PersonaRepository.PersonaAccess(false, false, false));
        when(personas.editablePersonaIds(777L)).thenReturn(List.of());

        assertEquals(Optional.of(List.of()), access.visibleChats());
        assertFalse(access.seesChat(-100L));
        assertFalse(access.canSeePersona(7));
        assertFalse(access.canEditPersona(7));
        assertEquals(Optional.of(List.of()), access.visiblePersonaIds());
        assertEquals(PersonaRepository.Scope.moderator(777L, List.of()), access.personaScope());
    }

    private void moderatorOf(List<Long> chats) {
        when(current.isOwner()).thenReturn(false);
        when(current.moderatorId()).thenReturn(Optional.of(777L));
        when(moderators.chatIdsOf(777L)).thenReturn(chats);
    }
}
