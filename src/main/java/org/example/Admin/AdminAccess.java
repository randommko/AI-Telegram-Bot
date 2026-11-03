package org.example.Admin;

import org.example.AiChat.PersonaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Права вошедшего: какие чаты видны и какие персоны он вправе править.
 * Владельцу видно всё; модератор ограничен выданными чатами, а персону правит,
 * только пока все её чаты — его.
 */
@Component
public class AdminAccess {
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

    /** Видна ли персона: у модератора — если хотя бы один её чат ему выдан. */
    public boolean canSeePersona(int personaId) {
        if (isOwner())
            return true;

        Optional<List<Long>> visible = visibleChats();
        if (visible.isEmpty())
            return true;

        List<Long> chats = visible.get();
        if (chats.isEmpty())
            return false;

        return personas.permission(personaId, chats).total() > 0;
    }

    /** Править персону может владелец или модератор, которому выданы все её чаты. */
    public boolean canEditPersona(int personaId) {
        if (isOwner())
            return true;

        Optional<List<Long>> visible = visibleChats();
        if (visible.isEmpty())
            return true;

        List<Long> chats = visible.get();
        if (chats.isEmpty())
            return false;

        PersonaRepository.PersonaPermission permission = personas.permission(personaId, chats);
        return permission.total() > 0 && permission.foreign() == 0;
    }

    /** Персоны, память которых модератору видна: ровно те, что он вправе править. */
    public Optional<List<Integer>> visiblePersonaIds() {
        if (isOwner())
            return Optional.empty();

        List<Long> chats = visibleChatIds();
        if (chats.isEmpty())
            return Optional.of(List.of());

        return Optional.of(personas.editablePersonaIds(chats));
    }

    public Optional<Actor> author() {
        return current.actor();
    }
}
