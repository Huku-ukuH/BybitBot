package org.example.bybit.service;

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
                    "category", "linear"
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
    }

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
}