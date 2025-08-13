package org.example.bybit.service;

import org.example.bybit.client.BybitHttpClient;
import org.example.monitor.dto.PositionInfo;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;

import java.io.IOException;
import java.util.Map;

public class BybitPositionTrackerService {
    private final BybitHttpClient httpClient;

    public BybitPositionTrackerService(BybitHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    /**
     * Получает актуальную позицию по символу
     */
    public PositionInfo getPosition(String symbol) throws IOException {
        try {
            Map<String, String> params = Map.of(
                    "category", "linear",
                    "symbol", symbol
            );

            // Вызов через ваш HttpClient
            String response = httpClient.signedGet("/v5/position/list", params, String.class);

            // Парсим через ваш JsonUtils
            Map<String, Object> root = JsonUtils.fromJson(response, Map.class);
            if (!"0".equals(root.get("retCode"))) {
                LoggerUtils.logWarn("Bybit вернул ошибку в /v5/position/list: " + root.get("retMsg"));
                return null;
            }

            Map<String, Object> result = (Map<String, Object>) root.get("result");
            if (result == null) return null;

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) result.get("list");
            if (list == null || list.isEmpty()) {
                return null; // Позиции нет
            }

            // Конвертируем первый элемент в PositionInfo
            String json = JsonUtils.toJson(list.get(0));
            return JsonUtils.fromJson(json, PositionInfo.class);

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при получении позиции для " + symbol, e);
            throw new IOException("Не удалось получить позицию", e);
        }
    }
}