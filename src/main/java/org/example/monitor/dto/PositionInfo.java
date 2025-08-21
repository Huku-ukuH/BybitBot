package org.example.monitor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import org.example.model.Direction;

/**
 * DTO для актуальной позиции с Bybit (v5/position/list)
 * Содержит ключевую информацию о текущей открытой позиции.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
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
    public double getRoi() {
        if (leverage == 0 || positionValue == 0) {
            return 0.0;
        }
        double initialMargin = positionValue / leverage;
        if (initialMargin == 0) return 0.0;
        return (unrealizedPnl / initialMargin) * 100.0;
    }
    public double getPotentialLoss() {
        double delta = Math.abs(entryPrice - stopLoss);
        return Math.round(size * delta * 1000.0) / 1000.0;
    }


    @Override
    public String toString() {
        return
                "\nUnrealPn:" + unrealizedPnl +
                ", realPnl:" + realizedPnl +
                "\nbustPrice:" + bustPrice +
                "\nisolatedMargin=" + isolatedMargin  +"potentialLoss:" + getPotentialLoss() + "\nROI" + getRoi() ;
    }
}