package org.example.aichat;

import java.time.Instant;

/**
 * Значение эмоции у персоны: baseValue — характер, value — настроение
 * на момент updatedAt. Действующее значение считается при чтении.
 */
public record PersonaEmotion(int personaId, String emotionCode, int baseValue, int value, Instant updatedAt) {
}
