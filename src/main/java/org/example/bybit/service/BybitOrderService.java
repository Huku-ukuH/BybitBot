package org.example.bybit.service;

import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.dto.SetLeverageResponse;
import org.example.bybit.client.BybitHttpClient;
import org.example.deal.Deal;
import org.example.deal.utils.OrderManager;
import org.example.model.Direction;
import org.example.result.OperationResult;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;
import java.util.HashMap;
import java.util.List;
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
                LoggerUtils.error(
                        "Ошибка создания ордера: " + response,
                        new IllegalStateException("BybitHttpClient вернул статус retMsg: " + response.getRetMsg()));
                return response;
            }

            LoggerUtils.info("BybitOrderResponse placeOrder()" + response);
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

            LoggerUtils.info("BybitOrderResponse setStopLoss() SlRequest" + slRequest + "\n" + "params" + params);
            return bybitHttpClient.signedPost("/v5/order/create", JsonUtils.toJson(params), BybitOrderResponse.class);

        } catch (Exception e) {
            LoggerUtils.error("❌setStopLoss() Ошибка установки стоп-лосса: ", e);
            throw new RuntimeException(e);
        }
    }

    public OperationResult cancelOrder(Deal deal, String orderId) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("symbol", deal.getSymbol().toString());
            body.put("category", "linear");

            String json = JsonUtils.toJson(body);
            bybitHttpClient.signedPost("/v5/order/cancel", json, Void.class);

        } catch (Exception e) {
            return OperationResult.failure("❌Ошибка отмены ордера " + deal.getSymbol() + " по цене " + findOrderPriceByOrderId(deal, orderId), e);
        }
        return OperationResult.success("Ордер " + deal.getSymbol() + " по цене " + findOrderPriceByOrderId(deal, orderId) + " - отменен");
    }

    public OperationResult cancelOrders(Deal deal) {
        StringBuilder cancelOrdersStringResult = new StringBuilder("Результат отмены TP SL для " + deal.getSymbol() + "\n");
        boolean hasErrors = false;

        for (OrderManager order : deal.getOrdersIdList()) {
            if (order.getOrderType() == OrderManager.OrderType.TP ||
                    order.getOrderType() == OrderManager.OrderType.SL) {

                OperationResult res = cancelOrder(deal, order.getOrderId());

                if (res.isSuccess()) {
                    cancelOrdersStringResult.append(res.getMessage()).append("\n");
                    continue;
                }

                res.logErrorIfFailed();
                cancelOrdersStringResult.append(res.getMessage()).append("\n");
                hasErrors = true;

            }
        }

        if (hasErrors) {
            return OperationResult.success("❌ЧАСТИЧНЫЙ УСПЕХ " + "\n" + cancelOrdersStringResult);
        }
        return OperationResult.success("Все TP и SL отменены или их не было");
    }

    public double findOrderPriceByOrderId(Deal deal, String orderId) {
        return deal.getOrdersIdList().stream()
                .filter(o -> orderId.equals(o.getOrderId()))
                .findFirst()
                .map(OrderManager::getOrderPrice)
                .orElse(Double.NaN);
    }


    public OperationResult closeDeal(Deal deal) {
        if (deal == null) {
            return OperationResult.failure("❌ Deal is null");
        }
        List<OrderManager> orders = deal.getOrdersIdList();
        if (orders == null || orders.isEmpty()) {
            return OperationResult.success("Нет ордеров для отмены (или ordersIdList = null)");
        }

        String symbol = deal.getSymbol().getSymbol();
        String side = deal.getDirection() == Direction.LONG ? "Sell" : "Buy";

        // --- Шаг 1: Отменяем TP и SL ---
        OperationResult cancelResult = cancelOrders(deal);
        String cancelReport = cancelResult.getMessage();

        // --- Шаг 2: Закрываем позицию ---
        try {
            Map<String, Object> params = Map.of(
                    "category", "linear",
                    "symbol", symbol,
                    "side", side
            );

            Object response = bybitHttpClient.signedPost("/v5/position/close-position", JsonUtils.toJson(params), Object.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response;
            Integer retCode = (Integer) result.get("retCode");

            StringBuilder closePositionResult = new StringBuilder();

            if (retCode != null && retCode == 0) {
                closePositionResult.append("✅ Сделка `").append(symbol).append("` успешно закрыта.\n\n");
                closePositionResult.append(cancelReport);
                return OperationResult.success(closePositionResult.toString());
            } else {
                String retMsg = (String) result.getOrDefault("retMsg", "неизвестная ошибка Bybit");
                closePositionResult.append("❌ Не удалось закрыть сделку: ").append(retMsg).append("\n\n");
                closePositionResult.append(cancelReport);
                return OperationResult.failure(closePositionResult.toString());
            }

        } catch (ClassCastException e) {
            LoggerUtils.error("Bybit: неожиданный формат ответа при закрытии " + symbol, e);
            String closePositionResult = "❌ Bybit вернул неожиданный формат ответа.\n\n" + cancelReport;
            return OperationResult.failure(closePositionResult, e);
        } catch (NullPointerException e) {
            LoggerUtils.error("Bybit: отсутствуют поля в ответе при закрытии " + symbol, e);
            String closePositionResult = "❌ Ответ от Bybit не содержит ожидаемых полей.\n\n" + cancelReport;
            return OperationResult.failure(closePositionResult, e);
        } catch (Exception e) {
            LoggerUtils.error("Техническая ошибка при закрытии позиции " + symbol, e);
            String closePositionResult = "❌ Техническая ошибка при закрытии позиции.\n\n" + cancelReport;
            return OperationResult.failure(closePositionResult, e);
        }
    }
}
