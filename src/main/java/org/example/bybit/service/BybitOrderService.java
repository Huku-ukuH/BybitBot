package org.example.bybit.service;

import org.example.bot.MessageSender;
import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.dto.SetLeverageResponse;
import org.example.bybit.dto.BybitOrderListResponse;
import org.example.bybit.client.BybitHttpClient;
import org.example.deal.Deal;
import org.example.strategy.params.PartialExitPlanner;
import org.example.deal.dto.PartialExitPlan;
import org.example.model.Direction;
import org.example.util.EmojiUtils;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;
import org.example.util.ValidationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BybitOrderService {
    private final BybitHttpClient bybitHttpClient;

    public BybitOrderService(BybitHttpClient bybitHttpClient) {
        this.bybitHttpClient = bybitHttpClient;
    }
    public BybitOrderResponse placeOrder(BybitOrderRequest request, Deal deal) {
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        try {
            // Отправка основного ордера
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


    public void placePartialTakeProfits(
            Deal deal,
            MessageSender messageSender,
            long chatId,
            StringBuilder result,
            BybitMarketService bybitMarketService
    ) throws Exception {

        List<Double> originalTakeProfits = deal.getTakeProfits();
        if (originalTakeProfits == null || originalTakeProfits.isEmpty()) {
            result.append("TP не задан\n");
            LoggerUtils.logDebug("placePartialTakeProfits() TP не задан");
            return;
        }

        double totalSize = deal.getPositionSize();
        String symbol = deal.getSymbol().toString();
        double minQty = bybitMarketService.getMinOrderQty(symbol);

        // Если сделка слишком мала — установить только 1 ближайший TP
        if (deal.isMinQty()) {
            double tpPrice = originalTakeProfits.get(0);
            double qty = bybitMarketService.roundLotSize(symbol, totalSize);

            if (qty < minQty) {
                messageSender.send(chatId, String.format(EmojiUtils.CROSS + " Даже один TP невозможно установить: qty %.3f < minQty %.3f", qty, minQty));
                return;
            }

            BybitOrderRequest tpRequest = new BybitOrderRequest();
            tpRequest.setSymbol(symbol);
            tpRequest.setSide(deal.getDirection() == Direction.LONG ? "Sell" : "Buy");
            tpRequest.setOrderType("Limit");
            tpRequest.setQty(String.format("%.3f", qty));
            tpRequest.setPrice(String.valueOf(tpPrice));
            tpRequest.setReduceOnly(true);
            tpRequest.setTimeInForce("GTC");

            String json = JsonUtils.toJson(tpRequest);
            BybitOrderResponse response = bybitHttpClient.signedPost("/v5/order/create", json, BybitOrderResponse.class);

            if (!"OK".equalsIgnoreCase(response.getRetMsg())) {
                throw new IllegalStateException(String.format("Ошибка установки TP: %.2f, qty: %.3f, причина: %s", tpPrice, qty, response.getRetMsg()));
            }

            messageSender.send(chatId, String.format(EmojiUtils.OKAY + " Установлен единственный TP %.2f (qty %.3f)", tpPrice, qty));
            result.append(EmojiUtils.OKAY).append("TP (min qty)\n");
            return;
        }

        // Иначе — отфильтруем доступные TP
        List<Double> validTakeProfits = new ArrayList<>();
        for (Double tp : originalTakeProfits) {
            double approxQty = totalSize / originalTakeProfits.size();
            double roundedQty = bybitMarketService.roundLotSize(symbol, approxQty);
            if (roundedQty >= minQty) {
                validTakeProfits.add(tp);
            }
        }

        if (validTakeProfits.isEmpty()) {
            messageSender.send(chatId, EmojiUtils.CROSS + " Ни один TP не может быть установлен: позиция слишком мала.");
            return;
        }

        // Новый план по допустимым TP
        PartialExitPlanner planner = new PartialExitPlanner();
        PartialExitPlan plan = planner.planExit(validTakeProfits);

        if (plan == null) {
            throw new IllegalStateException("Exit plan is null после фильтрации TP");
        }

        StringBuilder tpSummary = new StringBuilder();
        for (PartialExitPlan.ExitStep step : plan.getPartialExits()) {
            double tpPrice = step.getTakeProfit();
            int percentage = step.getPercentage();
            double qty = totalSize * percentage / 100.0;
            double roundedQty = bybitMarketService.roundLotSize(symbol, qty);

            if (roundedQty < minQty) {
                tpSummary.append(String.format(EmojiUtils.CROSS + " TP %.2f — qty %.3f < minQty %.3f, пропущен\n", tpPrice, roundedQty, minQty));
                continue;
            }

            BybitOrderRequest tpRequest = new BybitOrderRequest();
            tpRequest.setSymbol(symbol);
            tpRequest.setSide(deal.getDirection() == Direction.LONG ? "Sell" : "Buy");
            tpRequest.setOrderType("Limit");
            tpRequest.setQty(String.format("%.3f", roundedQty));
            tpRequest.setPrice(String.valueOf(tpPrice));
            tpRequest.setReduceOnly(true);
            tpRequest.setTimeInForce("GTC");

            String json = JsonUtils.toJson(tpRequest);
            BybitOrderResponse response = bybitHttpClient.signedPost("/v5/order/create", json, BybitOrderResponse.class);

            if (!"OK".equalsIgnoreCase(response.getRetMsg())) {
                throw new IllegalStateException(String.format(EmojiUtils.CROSS + "Ошибка установки TP:\n %.2f (%d%%), qty: %.3f, причина: %s",
                        tpPrice, percentage, roundedQty, response.getRetMsg()));
            }

            tpSummary.append(String.format(EmojiUtils.OKAY + " TP %.2f (%d%% pos, qty %.3f)\n", tpPrice, percentage, roundedQty));
        }

        result.append(EmojiUtils.OKAY).append("TP\n");
        messageSender.send(chatId, tpSummary.toString());
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
