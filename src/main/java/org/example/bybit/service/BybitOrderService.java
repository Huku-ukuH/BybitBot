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

    public void setLeverage(Deal deal) {
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

    public void cancelOrder(Deal deal, String orderId) {
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
    }
    public String closeDeal(Deal deal) {
        if (deal == null) {
            return "❌ Deal is null";
        }

        String symbol = deal.getSymbol().getSymbol();
        String side = deal.getDirection() == Direction.LONG ? "Sell" : "Buy";

        // --- Шаг 1: Отменяем TP и SL ---
        for (OrderManager order : deal.getOrdersIdList()) {
            if (order.getOrderType() == OrderManager.OrderType.TP ||
                    order.getOrderType() == OrderManager.OrderType.SL) {
                try {
                    cancelOrder(deal, order.getOrderId());
                } catch (Exception e) {
                    LoggerUtils.logError("⚠️ Не удалось отменить ордер " + order.getOrderId(), e);
                }
            }
        }

        // --- Шаг 2: Закрываем позицию ---
        try {
            Map<String, Object> params = Map.of(
                    "category", "linear",
                    "symbol", symbol,
                    "side", side
            );

            Object response = bybitHttpClient.signedPost("/v5/position/close-position", JsonUtils.toJson(params), Object.class);

            // Предполагаем, что ответ — это Map
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response;
            int retCode = (Integer) result.get("retCode");

            if (retCode == 0) {
                return "✅ Сделка `" + symbol + "` закрыта.";
            }
            return "❌ Ошибка: " + result.get("retMsg");

        } catch (ClassCastException e) {
            return "❌ Ошибка: неверный формат данных от сервера";
        } catch (NullPointerException e) {
            return "❌ Ошибка: неполные данные в ответе от API";
        } catch (Exception e) {
            return "❌ Ошибка: " + e.getMessage();
        }
    }
}
