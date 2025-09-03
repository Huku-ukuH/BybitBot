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

    public static BybitOrderRequest forEntry(Deal deal) {
        BybitOrderRequest request = new BybitOrderRequest();
        request.symbol = deal.getSymbol().toString();
        request.orderType = deal.getEntryType().toString().toLowerCase();
        request.side = deal.getDirection() == Direction.LONG ? "Buy" : "Sell";
        request.qty = String.valueOf(deal.getPositionSize());
        request.price = deal.getEntryPrice() != null ? String.valueOf(deal.getEntryPrice()) : null;
        request.reduceOnly = false;
        request.timeInForce = "GTC";
        return request;
    }
    public static BybitOrderRequest forTakeProfit(Deal deal, double price, double qty) {
        BybitOrderRequest request = new BybitOrderRequest();
        request.symbol = deal.getSymbol().toString();
        request.orderType = "Limit";
        request.side = deal.getDirection() == Direction.LONG ? "Sell" : "Buy";
        request.qty = String.format("%.3f", qty);
        request.price = String.valueOf(price);
        request.reduceOnly = true;
        request.timeInForce = "GTC";
        return request;
    }

    public static BybitOrderRequest forMarketCloseDeal(Deal deal) {
        BybitOrderRequest request = new BybitOrderRequest();
        request.symbol = deal.getSymbol().toString();
        request.orderType = "Market";
        request.side = deal.getDirection() == Direction.LONG ? "Sell" : "Buy";
        request.qty = String.valueOf(deal.getPositionSize());
        request.price = null;
        request.reduceOnly = true;
        request.timeInForce = "GTC";
        return request;
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
