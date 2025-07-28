package org.example.bybit.service;
import org.example.bybit.dto.InstrumentInfoResponse;
import org.example.bybit.dto.TickerResponse;
import org.example.bybit.client.BybitHttpClient;
import org.example.util.LoggerUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;


public class BybitMarketService {
    private final BybitHttpClient httpClient;
    private String accountCategorySpot = "spot";
    private String accountCategoryLinear = "linear";


    public BybitMarketService(BybitHttpClient httpClient) {
        this.httpClient = httpClient;
    }


    /**
     * Получает последнюю цену указанного символа на фьючерсном рынке Bybit (category = linear).
     *
     * @param symbol тикер (например: "BTCUSDT")
     * @return последняя цена (lastPrice)
     */
    public double getLastPrice(String symbol) {
        String endpoint = "/v5/market/tickers";
        Map<String, String> params = Map.of(
                "category", accountCategoryLinear, // фьючерсы (можно заменить на accountCategoryLinear, если нужно)
                "symbol", symbol
        );

        TickerResponse response = httpClient.get(endpoint, params, TickerResponse.class);
        List<TickerResponse.Ticker> tickers = response.getResult().getList();

        LoggerUtils.logInfo("ответ getLastPrice: " + tickers);
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

        double minOrderQty = list.get(0).getLotSizeFilter().getMinOrderQty();
        if (minOrderQty <= 0) {
            throw new IllegalStateException("Некорректный minOrderQty: " + minOrderQty);
        }

        return minOrderQty;
    }


    // тестовые методы для предотвращения некоректного тик сайза
    public double getLotSizeStep(String symbol) {
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

        double qtyStep = list.get(0).getLotSizeFilter().getQtyStep();
        if (qtyStep <= 0) {
            throw new IllegalStateException("Неверный qtyStep для символа: " + symbol + " (qtyStep=" + qtyStep + ")");
        }

        return qtyStep;
    }


    public double roundLotSize(String symbol, double quantity) {
        String endpoint = "/v5/market/instruments-info";
        Map<String, String> params = Map.of(
                "category", accountCategoryLinear,
                "symbol", symbol
        );

        InstrumentInfoResponse response = httpClient.get(endpoint, params, InstrumentInfoResponse.class);
        List<InstrumentInfoResponse.Instrument> list = response.getResult().getList();
        if (list == null || list.isEmpty()) {
            throw new RuntimeException("Информация отсутствует для " + symbol);
        }

        double stepSize = list.get(0).getLotSizeFilter().getQtyStep();
        double minQty = list.get(0).getLotSizeFilter().getMinOrderQty();

        if (stepSize <= 0 || minQty <= 0) {
            throw new IllegalStateException("Неверные параметры qtyStep или minQty для " + symbol +
                    " (qtyStep=" + stepSize + ", minQty=" + minQty + ")");
        }

        BigDecimal step = BigDecimal.valueOf(stepSize);
        BigDecimal qty = BigDecimal.valueOf(quantity);

        // Округляем ВНИЗ до ближайшего кратного stepSize
        BigDecimal rounded = qty.divide(step, 0, RoundingMode.DOWN).multiply(step);
        double result = rounded.doubleValue();

        // Если меньше минимального — устанавливаем минимально допустимое значение
        if (result < minQty) {
            LoggerUtils.logInfo("🔁 Кол-во " + result + " меньше minQty " + minQty + " — замена на minQty.");
            result = minQty;
        }

        return formatQuantity(result);
    }

    private static double formatQuantity(double value) {
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.stripTrailingZeros();
        String formatted = bd.toPlainString();
        return Double.parseDouble(formatted);
    }
}