package org.example.update;

public record CreationResult(
        boolean stillCreating,
        String message,
        int nextIndex
) {
    // Конструктор для завершения
    public CreationResult(boolean stillCreating, String message) {
        this(stillCreating, message, -1);
    }
}