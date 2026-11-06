package org.example.admin;

import org.example.core.aichat.PersonaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Права вошедшего: какие чаты видны и какие персоны он вправе править.
 * Владельцу видно всё. Модератору выданный чат открывает персону только для чтения,
 * а правит он свои персоны (создал сам) и те, что выдал владелец.
 */
@Component
public class AdminAccess {
    private static final long NO_MODERATOR = -1L;

    private final CurrentAdmin current;
    private final PersonaRepository personas;
    private final ChatModeratorRepository moderators;

    public AdminAccess(CurrentAdmin current, PersonaRepository personas, ChatModeratorRepository moderators) {
        this.current = current;
        this.personas = personas;
        this.moderators = moderators;
    }

    public boolean isOwner() {
        return current.isOwner();
    }

    /** Пустой Optional — ограничений нет; иначе только перечисленные чаты. */
    public Optional<List<Long>> visibleChats() {
        if (isOwner())
            return Optional.empty();

        return current.moderatorId()
                .map(moderators::chatIdsOf)
                .map(List::copyOf);
    }

    public List<Long> visibleChatIds() {
        return visibleChats().orElse(List.of());
    }

    public boolean seesChat(long chatId) {
        return visibleChats().map(chats -> chats.contains(chatId)).orElse(true);
    }

    /** Видна ли персона: свои, выданные и персоны выданных чатов. */
    public boolean canSeePersona(int personaId) {
        if (isOwner())
            return true;

        return access(personaId).visible();
    }

    /** Правку даёт авторство персоны или выдача владельцем; чат — только просмотр. */
    public boolean canEditPersona(int personaId) {
        if (isOwner())
            return true;

        return access(personaId).editable();
    }

    /** Область списка персон для модератора: чат, авторство и выдача. */
    public PersonaRepository.Scope personaScope() {
        if (isOwner())
            return PersonaRepository.Scope.owner();

        return PersonaRepository.Scope.moderator(
                current.moderatorId().orElse(NO_MODERATOR), visibleChatIds());
    }

    /** Персоны, память которых модератору видна: ровно те, что он вправе править. */
    public Optional<List<Integer>> visiblePersonaIds() {
        if (isOwner())
            return Optional.empty();

        Optional<Long> moderatorId = current.moderatorId();
        if (moderatorId.isEmpty())
            return Optional.of(List.of());

        return Optional.of(personas.editablePersonaIds(moderatorId.get()));
    }

    private PersonaRepository.PersonaAccess access(int personaId) {
        Optional<Long> moderatorId = current.moderatorId();
        if (moderatorId.isEmpty())
            return new PersonaRepository.PersonaAccess(false, false, false);

        return personas.personaAccess(personaId, moderatorId.get(), visibleChatIds());
    }

    public Optional<Long> authorId() {
        return current.actorTgUserId();
    }
}
