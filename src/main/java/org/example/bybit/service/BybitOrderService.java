package org.example.bybit.service;

import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.dto.SetLeverageResponse;
import org.example.bybit.client.BybitHttpClient;
import org.example.deal.Deal;
import org.example.deal.OrderManager;
import org.example.model.Direction;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;
import java.util.HashMap;
import java.util.Map;

public class BybitOrderService {
    private final BybitHttpClient bybitHttpClient;

    public BybitOrderService(BybitHttpClient bybitHttpClient) {
        this.bybitHttpClient = bybitHttpClient;
    }
    public BybitOrderResponse placeOrder(BybitOrderRequest request) {
        try {

            String jsonBody = JsonUtils.toJson(request);
            BybitOrderResponse response = bybitHttpClient.signedPost("/v5/order/create", jsonBody, BybitOrderResponse.class);

            if (!"OK".equalsIgnoreCase(response.getRetMsg())) {
                LoggerUtils.logError(
                        "Ошибка создания ордера: " + response,
                        new IllegalStateException("BybitHttpClient вернул статус retMsg: " + response.getRetMsg()));
                return response;
            }

            LoggerUtils.logInfo("BybitOrderResponse placeOrder()" + response);
            return response;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при выполнении ордера в placeOrder() ", e);
        }
    }

    public boolean setLeverage(Deal deal) {
        String leverage = String.valueOf(deal.getLeverageUsed());
        Map<String, String> params = Map.of(
                "category", "linear",
                "symbol", deal.getSymbol().toString(),
                "buyLeverage", leverage,
                "sellLeverage", leverage
        );

        SetLeverageResponse response = bybitHttpClient.signedPost("/v5/position/set-leverage", JsonUtils.toJson(params), SetLeverageResponse.class);

        if (!"OK".equalsIgnoreCase(response.getRetMsg())) {
            if (!response.getRetMsg().equalsIgnoreCase("leverage not modified")) {
                throw new IllegalStateException("Ошибка установки плеча: " + response.getRetMsg());
            }
        }


        return true;
    }

    public BybitOrderResponse setStopLoss(Deal deal) {
        try {
            BybitOrderRequest slRequest = new BybitOrderRequest();
            slRequest.setSymbol(deal.getSymbol().toString());
            slRequest.setSide(deal.getDirection() == Direction.LONG ? "Sell" : "Buy");
            slRequest.setOrderType("Market");
            slRequest.setQty(String.format("%.3f", deal.getPositionSize()));
            slRequest.setReduceOnly(true);


            // Стоп-лосс — это условный триггерный ордер
            Map<String, String> params = slRequest.toParamMap();
            params.put("triggerDirection", deal.getDirection() == Direction.LONG ? "2" : "1");
            params.put("triggerPrice", String.valueOf(deal.getStopLoss()));
            params.put("orderFilter", "StopOrder");
            params.put("category", "linear");

            LoggerUtils.logInfo("BybitOrderResponse setStopLoss() SlRequest" + slRequest + "\n" + "params" + params);
            return bybitHttpClient.signedPost("/v5/order/create", JsonUtils.toJson(params), BybitOrderResponse.class);

        } catch (Exception e) {
            LoggerUtils.logError("setStopLoss() Ошибка установки стоп-лосса: ", e);
            throw new RuntimeException(e);
        }
    }

    public String cancelOrder(Deal deal, String orderId) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("symbol", deal.getSymbol().toString());
            body.put("category", "linear");

            String json = JsonUtils.toJson(body);
            bybitHttpClient.signedPost("/v5/order/cancel", json, Void.class);

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка отмены ордера: ", e);
        }
        return "Ордер " + deal.getSymbol().toString() + " " + orderId + "отменен.";
    }
    public String closeDeal(Deal deal) {
        if (deal == null) {
            return "BybitOrderService.closeDeal() - Deal is null";
        }

        if (deal.getOrdersIdList() != null) {
            for (OrderManager order : deal.getOrdersIdList()) {
                if (order != null && order.getOrderId() != null && !order.getOrderId().isBlank()) {
                    try {
                        cancelOrder(deal, order.getOrderId());
                    } catch (Exception e) {
                        LoggerUtils.logError("closeDeal() ❌ Ошибка при отмене ордера: " + order, e);
                    }
                }
            }
        } else {
            LoggerUtils.logInfo("ℹ️ Нет ордеров для отмены для сделки: " + deal.getSymbol());
        }

        // Закрываем позицию по рынку
        try {
            BybitOrderRequest request = BybitOrderRequest.forMarketCloseDeal(deal);
            placeOrder(request);

        } catch (Exception e) {
            LoggerUtils.logError("❌ Ошибка при закрытии позиции: " + deal.getSymbol(), e);
        }
        return "closeDeal() Сделка закрыта по рынку:";
    }
}
