package org.example.core.aichat;

/** Персона, привязанная к чату: её характер и имя, по которому к ней обращаются. */
public record Persona(int id, String name, String prompt) {
}
