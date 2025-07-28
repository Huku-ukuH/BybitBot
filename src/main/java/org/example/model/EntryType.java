package org.example.model;

import com.fasterxml.jackson.annotation.JsonCreator;

//Класс Определяет тип ордера — MARKET или LIMIT.
public enum EntryType {
    MARKET,
    LIMIT;

    @JsonCreator
    public static EntryType fromString(String value) {
        if (value == null) return null;
        return switch (value.trim().toUpperCase()) {
            case "MARKET", "MKT" -> MARKET;
            case "LIMIT", "LMT" -> LIMIT;
            default -> throw new IllegalArgumentException("Unknown order type: " + value);
        };
    }
}