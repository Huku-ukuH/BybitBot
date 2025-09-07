package org.example.monitor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceUpdate {
    @JsonProperty("symbol")
    private final String symbol;
    @JsonProperty("price")
    private final double price;

    public PriceUpdate(String symbol, double price) {
        this.symbol = symbol;
        this.price = price;
    }

    // геттеры
}
