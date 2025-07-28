package org.example.model;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
@Getter

//Класс редставляет торговый символ, например: "BTCUSDT", "ETHUSDT" и т.д.
public class Symbol {
    private final String symbol;

    @JsonCreator
    public Symbol(String symbol) {
        if (symbol == null || !symbol.toUpperCase().matches("[A-Z0-9]+")) {
            throw new IllegalArgumentException("Invalid symbol format: " + symbol);
        }
        if (!symbol.contains("USDT")) {
            symbol = symbol + "USDT";
        }
        this.symbol = symbol.toUpperCase();
    }

    @Override
    public String toString() {
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Symbol other = (Symbol) o;
        return symbol.equals(other.symbol);
    }

    @Override
    public int hashCode() {
        return symbol.hashCode();
    }
}
