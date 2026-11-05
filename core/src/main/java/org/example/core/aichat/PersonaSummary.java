package org.example.core.aichat;

/** Строка списка персон: счётчики и привязанные чаты одной строкой для админки. */
public record PersonaSummary(int id, String name, int aliasCount, int memoryCount, String chats) {
}
