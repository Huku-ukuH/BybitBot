package org.example.bybit.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.example.bybit.client.BybitHttpClient;
import org.example.monitor.dto.PositionInfo;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BybitPositionTrackerService {
    private final BybitHttpClient httpClient;

    public BybitPositionTrackerService(BybitHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Получает список всех активных позиций с биржи Bybit.
     *
     * @return список позиций, может быть пустым, но не null
     * @throws IOException при ошибках сети или парсинга
     */
    public List<PositionInfo> getPositionList() throws IOException {
        try {
            Map<String, String> params = Map.of(
                    "category", "linear",
                    "settleCoin", "USDT"
            );

            // ✅ Шаг 1: Получаем как Object — Jackson примет JSON и сделает Map
            Object rawResponse = httpClient.signedGet("/v5/position/list", params, Object.class);

            // ✅ Шаг 2: Превращаем Object -> JSON строку
            String jsonString = JsonUtils.toJson(rawResponse);

            // ✅ Шаг 3: Теперь парсим как карта
            Map<String, Object> root = JsonUtils.fromJson(jsonString, Map.class);
            System.out.println(root.toString());

            if (!Integer.valueOf(0).equals(root.get("retCode"))) {
                String errorMsg = (String) root.get("retMsg");
                LoggerUtils.warn("Bybit вернул ошибку в /v5/position/list: " + errorMsg);
                return Collections.emptyList();
            }

            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }

            return list.stream()
                    .map(item -> {
                        try {
                            String json = JsonUtils.toJson(item);
                            return JsonUtils.fromJson(json, PositionInfo.class);
                        } catch (Exception e) {
                            LoggerUtils.error("Ошибка конвертации позиции из JSON: " + item, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtils.error("Ошибка при получении списка позиций", e);
            throw new IOException("Не удалось получить список позиций", e);
        }
    }


    //класс для получения ордеров, для создания новых сделок

    /**
     * DTO для ордера с Bybit API. Включает все поля, необходимые для восстановления TP/SL.
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderInfo {
        @JsonProperty("orderId")
        private String orderId;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("side")
        private String side;

        @JsonProperty("qty")
        private String qty;

        @JsonProperty("price")
        private String price; // для лимитных ордеров

        @JsonProperty("orderType")
        private String orderType; // "Market", "Limit"

        @JsonProperty("reduceOnly")
        private Boolean reduceOnly; // true для TP/SL

        @JsonProperty("stopOrderType")
        private String stopOrderType; // "StopLoss", "TakeProfit", "PartialTakeProfit"

        @JsonProperty("triggerPrice")
        private String triggerPrice; // цена активации (триггер)

        @JsonProperty("tpslMode")
        private String tpslMode; // "Full", "Partial"

        @JsonProperty("orderStatus")
        private String orderStatus; // "New", "Untriggered", "Triggered"

        @Override
        public String toString() {
            return "OrderInfo{" +
                    "orderId='" + orderId + '\'' +
                    ", symbol='" + symbol + '\'' +
                    ", side='" + side + '\'' +
                    ", qty='" + qty + '\'' +
                    ", price='" + price + '\'' +
                    ", orderType='" + orderType + '\'' +
                    ", reduceOnly=" + reduceOnly +
                    ", stopOrderType='" + stopOrderType + '\'' +
                    ", triggerPrice='" + triggerPrice + '\'' +
                    ", tpslMode='" + tpslMode + '\'' +
                    ", orderStatus='" + orderStatus + '\'' +
                    '}';
        }
    }

    /**
     * Получает список активных ордеров по символу.
     * <p>
     * Важно: использует {@code stopOrderType} и {@code triggerPrice}, а не {@code orderType}.
     *
     * @param symbol Торговая пара, например "ETHUSDT"
     * @return Список объектов OrderInfo (включая orderId)
     * @throws IOException при ошибках сети или парсинга
     */
    public List<OrderInfo> getOrders(String symbol) throws IOException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("category", "linear");
            params.put("symbol", symbol);

            // Получаем ответ как Object → сериализуем в JSON строку → парсим в Map
            Object rawResponse = httpClient.signedGet("/v5/order/realtime", params, Object.class);
            String jsonString = JsonUtils.toJson(rawResponse);
            Map<String, Object> root = JsonUtils.fromJson(jsonString, Map.class);

            // Проверяем retCode как Integer
            if (!Integer.valueOf(0).equals(root.get("retCode"))) {
                String errorMsg = (String) root.getOrDefault("retMsg", "Unknown error");
                LoggerUtils.warn("Bybit вернул ошибку в /v5/order/realtime: " + errorMsg);
                return Collections.emptyList();
            }

            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) {
                LoggerUtils.debug("Ответ от /v5/order/realtime: 'result' is null");
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
            if (list == null || list.isEmpty()) {
                LoggerUtils.debug("Список ордеров пуст для символа: " + symbol);
                return Collections.emptyList();
            }

            // Конвертируем каждый элемент в OrderInfo
            return list.stream()
                    .map(item -> {
                        try {
                            String json = JsonUtils.toJson(item);
                            return JsonUtils.fromJson(json, OrderInfo.class);
                        } catch (Exception e) {
                            LoggerUtils.error("Ошибка конвертации ордера из JSON: " + item, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtils.error("Ошибка при получении списка ордеров для символа " + symbol, e);
            throw new IOException("Не удалось получить список ордеров", e);
        }
    }
}