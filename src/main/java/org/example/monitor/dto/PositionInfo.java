package org.example.monitor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.example.model.Direction;

/**
 * DTO для актуальной позиции с Bybit (v5/position/list)
 * Содержит ключевую информацию о текущей открытой позиции.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)

public class PositionInfo {

    @JsonProperty("symbol")
    private String symbol;
    @JsonProperty("side")
    private String side;
    @JsonProperty("size")
    private double size;
    @JsonProperty("entryPrice")
    private double entryPrice;
    @JsonProperty("leverage")
    private double leverage;
    @JsonProperty("positionValue")
    private double positionValue;         //стоимость
    @JsonProperty("unrealizedPnl")
    private double unrealizedPnl;        //нереализ pnl
    @JsonProperty("cumulatedRealisedPnl")
    private double realizedPnl;          //реализ pnl
    @JsonProperty("tpSlMode")
    private String tpSlMode;             //Режим установки TP/SL: "Full" (полное закрытие), "Partial" (частичное)
    @JsonProperty("positionStatus")
    private String positionStatus;       //Статус позиции: "Normal", "Liq" (ликвидация), "Adl" (ADL), "Closed"
    @JsonProperty("bustPrice")
    private double bustPrice;            //цена ликвидации
    @JsonProperty("stopLoss")
    private double stopLoss;
    @JsonProperty("takeProfit")
    private double takeProfit;
    @JsonProperty("trailingStop")
    private double trailingStop;
    @JsonProperty("isolatedMargin")
    private double isolatedMargin;      //Размер изолированной маржи (в USDT)



    // --- Дополнительные удобные методы ---

    /**
     * Является ли позиция активной (не закрыта и не в ликвидации)
     */
    public boolean isActive() {
        return "Normal".equals(positionStatus);
    }

    /**
     * Возвращает направление позиции в виде Direction.LONG / Direction.SHORT
     */
    public Direction getDirection() {
        return "Buy".equalsIgnoreCase(side) ? Direction.LONG : Direction.SHORT;
    }

    /**
     * Нереализованная прибыль в процентах от изолированной маржи (ROI)
     * Это и есть ROI для текущей позиции.
     */
    public double getRoi() {
        if (isolatedMargin == 0) {
            return 0.0;
        }
        return (unrealizedPnl / isolatedMargin) * 100.0;
    }

    /**
     * Удобное строковое представление ROI
     */
    public String getRoiFormatted() {
        return String.format("%.2f%%", getRoi());
    }
}