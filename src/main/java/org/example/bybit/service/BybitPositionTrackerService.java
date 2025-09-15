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
                LoggerUtils.logWarn("Bybit вернул ошибку в /v5/position/list: " + errorMsg);
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
                            LoggerUtils.logError("Ошибка конвертации позиции из JSON: " + item, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при получении списка позиций", e);
            throw new IOException("Не удалось получить список позиций", e);
        }
    }
/*    public List<PositionInfo> getPositionList() throws IOException {
        try {
            Map<String, String> params = Map.of(
                    "category", "linear",
                    "settleCoin", "USDT"
                    // Можно добавить фильтры при необходимости
            );

            String response = httpClient.signedGet("/v5/position/list", params, String.class);
            Map<String, Object> root = JsonUtils.fromJson(response, Map.class);

            if (!"0".equals(root.get("retCode"))) {
                String errorMsg = (String) root.get("retMsg");
                LoggerUtils.logWarn("Bybit вернул ошибку в /v5/position/list: " + errorMsg);
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

            // Конвертируем каждый элемент в PositionInfo
            return list.stream()
                    .map(item -> {
                        try {
                            String json = JsonUtils.toJson(item);
                            return JsonUtils.fromJson(json, PositionInfo.class);
                        } catch (Exception e) {
                            LoggerUtils.logError("Ошибка конвертации позиции из JSON: " + item, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при получении списка позиций", e);
            throw new IOException("Не удалось получить список позиций", e);
        }
    }*/

    /**
     * Получает позицию по конкретному символу.
     *
     * @param symbol символ, например "BTCUSDT"
     * @return позиция или null, если не найдена
     * @throws IOException при ошибках сети или парсинга
     */
    public PositionInfo getPosition(String symbol) throws IOException {
        List<PositionInfo> positions = getPositionList();
        return positions.stream()
                .filter(p -> symbol.equals(p.getSymbol()))
                .findFirst()
                .orElse(null);
    }
    public PositionInfo getPosition(List<PositionInfo> positions, String symbol) {
        if (positions == null || symbol == null) {
            return null;
        }

        return positions.stream()
                .filter(p -> symbol.equals(p.getSymbol()))  // безопасно при p.getSymbol() == null
                .findFirst()
                .orElse(null);
    }


    //класс для получения ордеров, для создания новых сделок

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
        private String price;


        @Override
        public String toString() {
            return "OrderInfo{" +
                    "orderId='" + orderId + '\'' +
                    ", symbol='" + symbol + '\'' +
                    ", side='" + side + '\'' +
                    ", qty='" + qty + '\'' +
                    ", price='" + price + '\'' +
                    '}';
        }
    }

    /**
     * Получает список активных/недавних ордеров и возвращает их ID.
     * Можно фильтровать по символу и orderLinkId.
     *
     * @param symbol      Торговая пара, например "BTCUSDT"
     * @return Список объектов OrderInfo (включая orderId)
     * @throws IOException при ошибках сети или парсинга
     */
/*    public List<OrderInfo> getOrders(String symbol) throws IOException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("category", "linear");
            params.put("symbol", symbol);

            String response = httpClient.signedGet("/v5/order/realtime", params, String.class);
            Map<String, Object> root = JsonUtils.fromJson(response, Map.class);

            if (!"0".equals(root.get("retCode"))) {
                String errorMsg = (String) root.get("retMsg");
                LoggerUtils.logWarn("Bybit вернул ошибку в /v5/order/realtime: " + errorMsg);
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
                            return JsonUtils.fromJson(json, OrderInfo.class);
                        } catch (Exception e) {
                            LoggerUtils.logError("Ошибка конвертации ордера из JSON: " + item, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при получении списка ордеров", e);
            throw new IOException("Не удалось получить список ордеров", e);
        }
    }*/

    public List<OrderInfo> getOrders(String symbol) throws IOException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("category", "linear");
            params.put("symbol", symbol);

            // ✅ Аналогично — получаем как Object
            Object rawResponse = httpClient.signedGet("/v5/order/realtime", params, Object.class);
            String jsonString = JsonUtils.toJson(rawResponse);
            Map<String, Object> root = JsonUtils.fromJson(jsonString, Map.class);

            if (!"0".equals(root.get("retCode"))) {
                String errorMsg = (String) root.get("retMsg");
                LoggerUtils.logWarn("Bybit вернул ошибку в /v5/order/realtime: " + errorMsg);
                return Collections.emptyList();
            }

            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) return Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
            if (list == null || list.isEmpty()) return Collections.emptyList();

            return list.stream()
                    .map(item -> {
                        try {
                            String json = JsonUtils.toJson(item);
                            return JsonUtils.fromJson(json, OrderInfo.class);
                        } catch (Exception e) {
                            LoggerUtils.logError("Ошибка конвертации ордера из JSON: " + item, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при получении списка ордеров", e);
            throw new IOException("Не удалось получить список ордеров", e);
        }
    }

    /**
     * Удобный метод: получить только список ID ордеров
     */
    public List<String> getOrderIds(String symbol) throws IOException {
        return getOrders(symbol).stream()
                .map(OrderInfo::getOrderId)
                .collect(Collectors.toList());
    }
}