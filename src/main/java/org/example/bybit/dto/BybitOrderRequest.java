package org.example.bybit.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deal.Deal;
import org.example.model.Direction;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO для создания ордера на Bybit на основе объекта Deal.
 */
@Data
@NoArgsConstructor
public class BybitOrderRequest {

    private String category = "linear";
    private String symbol;
    private String side;
    private String orderType;
    private String qty;
    private String price;      // В данном случае можно использовать entryPrice как цену лимитного входа
    private String timeInForce = "GTC";
    private Boolean reduceOnly;

    public BybitOrderRequest(Deal deal) { //конструктор на вход
        this.symbol = deal.getSymbol().toString();
        this.orderType = deal.getEntryType().toString().toLowerCase(); // "market" или "limit"
        this.side = deal.getDirection() == Direction.LONG ? "Buy" : "Sell";
        this.qty = String.valueOf(deal.getPositionSize());
        this.price = deal.getEntryPrice() != null ? String.valueOf(deal.getEntryPrice()) : null;
        reduceOnly = false;// по умолчанию — вход, не выход
    }

    public BybitOrderRequest(Deal deal, double price, double qty) { //конструктор для выхода
        this.symbol = deal.getSymbol().toString();
        this.orderType = "Limit";
        this.side = deal.getDirection() == Direction.LONG ? "Sell" : "Buy";
        this.qty = String.format("%.3f", qty);        // форматируем количество
        this.price = String.valueOf( price);    // форматируем цену
        this.reduceOnly = true; // Критически важно: только уменьшение позиции
        this.timeInForce = "GTC"; // Good 'Til Canceled
    }

    public Map<String, String> toParamMap() {
        Map<String, String> params = new HashMap<>();
        params.put("category", category);
        params.put("symbol", symbol);
        params.put("side", side);
        params.put("orderType", orderType);
        params.put("qty", qty);

        if (reduceOnly != null) {
            params.put("reduceOnly", reduceOnly.toString());
        }

        if (price != null && !price.isBlank()) {
            params.put("price", price);
        }

        if (!"market".equalsIgnoreCase(orderType)) {
            params.put("timeInForce", timeInForce);
        }

        return params;
    }
}
