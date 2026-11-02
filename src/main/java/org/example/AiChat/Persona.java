package org.example.AiChat;

/** Персона, привязанная к чату: её характер и имя, по которому к ней обращаются. */
public record Persona(int id, String name, String prompt) {
}
