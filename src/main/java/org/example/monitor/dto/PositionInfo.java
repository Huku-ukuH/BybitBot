package org.example.monitor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import org.example.model.Direction;
import org.example.model.Symbol;
import org.example.util.LoggerUtils;

/**
 * DTO для актуальной позиции с Bybit (v5/position/list)
 * Содержит ключевую информацию о текущей открытой позиции.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class PositionInfo {

    @JsonProperty("symbol")
    private Symbol symbol;
    @JsonProperty("side")
    private Direction side;
    @JsonProperty("size")
    private double size;
    @JsonProperty("avgPrice")
    private double avgPrice;
    @JsonProperty("leverage")
    private double leverage;
    @JsonProperty("positionValue")
    private double positionValue;         //стоимость
    @JsonProperty("unrealisedPnl")
    private double unrealisedPnl;        //нереализ pnl
    @JsonProperty("cumulatedRealisedPnl")
    private double realizedPnl;          //реализ pnl
    @JsonProperty("tpslMode")
    private String tpslMode;             //Режим установки TP/SL: "Full" (полное закрытие), "Partial" (частичное)
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

        return (unrealisedPnl / initialMargin) * 100.0;
    }
    public double getPotentialLoss() {
        return Math.round(size * Math.abs(avgPrice - stopLoss) * 1000.0) / 1000.0;
    }


    @Override
    public String toString() {
//        LoggerUtils.logInfo("ПОЛНАЯ ИНФОРМАЦИЯ О ПОЗИЦИИ С БАЙБИТ \nPositionInfo{" +
//                "symbol='" + symbol + '\'' +
//                ", side='" + side + '\'' +
//                ", size=" + size +
//                ", entryPrice=" + avgPrice +
//                ", leverage=" + leverage +
//                ", positionValue=" + positionValue +
//                ", unrealizedPnl=" + unrealisedPnl +
//                ", realizedPnl=" + realizedPnl +
//                ", tpSlMode='" + tpslMode + '\'' +
//                ", positionStatus='" + positionStatus + '\'' +
//                ", bustPrice=" + bustPrice +
//                ", stopLoss=" + stopLoss +
//                ", takeProfit=" + takeProfit +
//                ", trailingStop=" + trailingStop +
//                ", isolatedMargin=" + isolatedMargin +
//                '}');

         return "\nUnrealisedPnl = " + unrealisedPnl +
                "\nrealisedPnl = " + realizedPnl +
                "\nbustPrice:" + bustPrice +
                "\nisolatedMargin=" + isolatedMargin  +
                "\npotentialLoss:" + getPotentialLoss() +
                 "\nROI" + getRoi() ;
    }
}