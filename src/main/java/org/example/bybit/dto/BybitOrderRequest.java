package org.example.bybit.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deal.Deal;
import org.example.model.Direction;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private Boolean reduceOnly = false; // по умолчанию — вход, не выход

    public BybitOrderRequest(Deal deal) {
        this.symbol = deal.getSymbol().toString();
        this.orderType = deal.getEntryType().toString().toLowerCase(); // "market" или "limit"
        this.side = deal.getDirection() == Direction.LONG ? "Buy" : "Sell";
        this.qty = String.valueOf(deal.getPositionSize());
        this.price = deal.getEntryPrice() != null ? String.valueOf(deal.getEntryPrice()) : null;
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
