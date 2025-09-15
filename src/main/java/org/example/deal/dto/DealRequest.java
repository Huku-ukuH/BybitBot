package org.example.deal.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.example.model.Symbol;
import org.example.model.Direction;
import org.example.model.EntryType;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)

// Данные о сделке после парсинга сигнала от пользователя

public class DealRequest {
    @JsonProperty("symbol")
    private Symbol symbol;
    @JsonProperty("direction")
    private Direction direction;
    @JsonProperty("entry_type")
    private EntryType entryType;

    @JsonProperty("entry_price")
    private Double entryPrice;

    @JsonProperty("stop_loss")
    private Double stopLoss;

    @JsonProperty("take_profits")
    private List<Double> takeProfits;

}

