package org.example.bybit.service;

import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.dto.SetLeverageResponse;
import org.example.bybit.dto.BybitOrderListResponse;
import org.example.bybit.client.BybitHttpClient;
import org.example.deal.Deal;
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

        String json = JsonUtils.toJson(params);
        SetLeverageResponse response = bybitHttpClient.signedPost("/v5/position/set-leverage", json, SetLeverageResponse.class);

        if (!"OK".equalsIgnoreCase(response.getRetMsg())) {
            // не бросаем исключение, если плечо не изменилось
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

            String json = JsonUtils.toJson(params);
            return bybitHttpClient.signedPost("/v5/order/create", json, BybitOrderResponse.class);

        } catch (Exception e) {
            LoggerUtils.logError("setStopLoss() Ошибка установки стоп-лосса: ", e);
            throw new RuntimeException(e);
        }
    }
    public void closeLimitOrders(Deal deal) {
        String symbol = deal.getSymbol().toString();
        try {
            Map<String, String> params = Map.of(
                    "category", "linear",
                    "symbol", symbol
            );
            BybitOrderListResponse response = bybitHttpClient.signedGet("/v5/order/realtime", params, BybitOrderListResponse.class);

            for (BybitOrderListResponse.Order order : response.getResult().getList()) {
                boolean isLimit = "Limit".equalsIgnoreCase(order.getOrderType());
                boolean isSameSide = order.getSide().equalsIgnoreCase(deal.getDirection() == Direction.LONG ? "Buy" : "Sell");

                if (isLimit && isSameSide) {
                    cancelOrder(order.getOrderId(), symbol);
                    LoggerUtils.logInfo("Отменён лимитный ордер: " + order.getOrderId());
                }
            }
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при отмене лимитных ордеров: ", e);
        }
    }

    public void closeTakeProfits(Deal deal) {
        String symbol = deal.getSymbol().toString();
        try {
            Map<String, String> params = Map.of(
                    "category", "linear",
                    "symbol", symbol
            );
            BybitOrderListResponse response = bybitHttpClient.signedGet("/v5/order/realtime", params, BybitOrderListResponse.class);

            for (BybitOrderListResponse.Order order : response.getResult().getList()) {
                if (order.isReduceOnly() && "Limit".equalsIgnoreCase(order.getOrderType())
                        && order.getSide().equalsIgnoreCase(deal.getDirection() == Direction.LONG ? "Sell" : "Buy")) {
                    cancelOrder(order.getOrderId(), symbol);
                    LoggerUtils.logInfo("Отменён тейк-профит: " + order.getOrderId());
                }
            }
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при отмене тейк-профитов: ", e);
        }
    }

    public void closeStopLoss(Deal deal) {
        String symbol = deal.getSymbol().toString();
        try {
            Map<String, String> params = Map.of(
                    "category", "linear",
                    "symbol", symbol
            );
            BybitOrderListResponse response = bybitHttpClient.signedGet("/v5/order/realtime", params, BybitOrderListResponse.class);

            for (BybitOrderListResponse.Order order : response.getResult().getList()) {
                boolean isStop = "Market".equalsIgnoreCase(order.getOrderType()) || "Stop".equalsIgnoreCase(order.getOrderType());
                if (order.isReduceOnly() && isStop
                        && order.getSide().equalsIgnoreCase(deal.getDirection() == Direction.LONG ? "Sell" : "Buy")) {
                    cancelOrder(order.getOrderId(), symbol);
                    LoggerUtils.logInfo("Отменён стоп-лосс: " + order.getOrderId());
                }
            }
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при отмене стоп-лосса: ", e);
        }
    }
    public void cancelOrder(String orderId, String symbol) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("symbol", symbol);
            body.put("category", "linear");

            String json = JsonUtils.toJson(body);

            bybitHttpClient.signedPost("/v5/order/cancel", json, Void.class);

            LoggerUtils.logInfo("Ордер отменён: " + orderId);

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка отмены ордера: ", e);
        }
    }

}
