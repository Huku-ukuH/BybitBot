package org.example.model;

import com.fasterxml.jackson.annotation.JsonCreator;

//Класс Описывает направление сделки — long или short.
public enum Direction {
    LONG,
    SHORT;

    @JsonCreator
    public static Direction fromString(String value) {
        if (value == null) return null;
        return switch (value.trim().toUpperCase()) {
            case "LONG", "BUY" -> LONG;
            case "SHORT", "SELL" -> SHORT;
            default -> throw new IllegalArgumentException("Class Direction, ошибка направления: " + value);
        };
    }

    @Override
    public String toString() {
        return name();
    }
}
