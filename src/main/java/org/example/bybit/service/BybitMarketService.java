package org.example.bybit.service;

import org.example.bybit.dto.InstrumentInfoResponse;
import org.example.bybit.dto.TickerResponse;
import org.example.bybit.client.BybitHttpClient;
import org.example.util.LoggerUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BybitMarketService {
    private final BybitHttpClient httpClient;
    private String accountCategorySpot = "spot";
    private String accountCategoryLinear = "linear";
    private final Map<String, InstrumentInfoResponse.Instrument> instrumentInfoCache = new HashMap<>();

    public BybitMarketService(BybitHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public double getLastPrice(String symbol) {
        String endpoint = "/v5/market/tickers";
        Map<String, String> params = Map.of(
                "category", accountCategoryLinear,
                "symbol", symbol
        );

        TickerResponse response = httpClient.get(endpoint, params, TickerResponse.class);
        List<TickerResponse.Ticker> tickers = response.getResult().getList();

        LoggerUtils.logDebug("ответ getLastPrice для " + symbol + ": " + tickers); // Изменено на logDebug
        if (tickers == null || tickers.isEmpty()) {
            throw new RuntimeException("Пустой список тикеров для символа: " + symbol);
        }

        try {
            return Double.parseDouble(tickers.get(0).getLastPrice());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Не удалось преобразовать цену: " + tickers.get(0).getLastPrice(), e);
        }
    }

    public double getMinOrderQty(String symbol) {
        InstrumentInfoResponse.Instrument instrumentInfo = getInstrumentInfoFromCacheOrApi(symbol);
        double minOrderQty = instrumentInfo.getLotSizeFilter().getMinOrderQty();
        LoggerUtils.logDebug("Информация о " + symbol + " получена из кэша. MinOrderQty =" + minOrderQty);
        if (minOrderQty <= 0) {
            throw new IllegalStateException("Некорректный minOrderQty для " + symbol + ": " + minOrderQty);
        }
        LoggerUtils.logDebug("Минимальное количество для ордера (" + symbol + "): " + minOrderQty);
        return minOrderQty;
    }

    //  Извлекает шаг приращения
    public double getLotSizeStep(String symbol) {
        InstrumentInfoResponse.Instrument instrumentInfo = getInstrumentInfoFromCacheOrApi(symbol);
        double qtyStep = instrumentInfo.getLotSizeFilter().getQtyStep();
        LoggerUtils.logDebug("Информация о " + symbol + " получена из кэша. qtyStep =" + qtyStep);
        if (qtyStep <= 0) {
            throw new IllegalStateException("Неверный qtyStep для символа: " + symbol + " (qtyStep=" + qtyStep + ")");
        }
        return qtyStep;
    }

    // корректирует quantity чтобы оно соответствовало правилам minOrderQty и qtyStep
    public double roundLotSize(String symbol, double quantity) {
        // 1. Проверка на недопустимые входные данные
        if (Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity < 0) {
            throw new IllegalArgumentException("Недопустимое количество для округления: " + quantity);
        }

        InstrumentInfoResponse.Instrument instrumentInfo = getInstrumentInfoFromCacheOrApi(symbol);

        double stepSize = instrumentInfo.getLotSizeFilter().getQtyStep();
        double minQty = instrumentInfo.getLotSizeFilter().getMinOrderQty();
        LoggerUtils.logDebug("Информация о stepSize и minQty для" + symbol + " получена из кэша.\n stepSize =" + stepSize + "\nminQty =" + minQty);


        if (stepSize <= 0 || minQty <= 0) {
            throw new IllegalStateException("Неверные параметры qtyStep или minQty для " + symbol +
                    " (qtyStep=" + stepSize + ", minQty=" + minQty + ")");
        }

        if (quantity < minQty) {
            LoggerUtils.logDebug("🔁 Входное кол-во " + quantity + " меньше minQty " + minQty + " — замена на minQty.");
            return formatQuantity(minQty);
        }

        BigDecimal step = BigDecimal.valueOf(stepSize);
        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal rounded = qty.divide(step, 0, RoundingMode.DOWN).multiply(step);
        double result = rounded.doubleValue();

        if (result < minQty) {
            LoggerUtils.logWarn("⚠️ После округления кол-во " + result + " оказалось меньше minQty " + minQty +
                    " для символа " + symbol + ". Возвращаем minQty.");
            result = minQty;
        }

        return formatQuantity(result);
    }


    private InstrumentInfoResponse.Instrument getInstrumentInfoFromCacheOrApi(String symbol) {

        InstrumentInfoResponse.Instrument cachedInfo = instrumentInfoCache.get(symbol);
        if (cachedInfo != null) {
            return cachedInfo;
        }

        LoggerUtils.logDebug("Информация об инструменте " + symbol + " отсутствует в кэше. Запрос к API Bybit.");
        String endpoint = "/v5/market/instruments-info";
        Map<String, String> params = Map.of(
                "category", accountCategoryLinear,
                "symbol", symbol
        );

        InstrumentInfoResponse response = httpClient.get(endpoint, params, InstrumentInfoResponse.class);
        List<InstrumentInfoResponse.Instrument> list = response.getResult().getList();
        if (list == null || list.isEmpty()) {
            throw new RuntimeException("Информация по инструментам отсутствует для символа: " + symbol);
        }

        InstrumentInfoResponse.Instrument instrumentInfo = list.get(0);

        // 3. Сохраняем в кэш
        instrumentInfoCache.put(symbol, instrumentInfo);
        LoggerUtils.logDebug("Информация об инструменте " + symbol + " сохранена в кэш.");

        return instrumentInfo;
    }
    // -----------------------------------

    private static double formatQuantity(double value) {
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.stripTrailingZeros();
        String formatted = bd.toPlainString();
        return Double.parseDouble(formatted);
    }
}