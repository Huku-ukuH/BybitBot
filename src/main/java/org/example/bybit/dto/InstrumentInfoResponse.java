package org.example.bybit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstrumentInfoResponse {

    @JsonProperty("retCode")
    private int retCode;

    @JsonProperty("retMsg")
    private String retMsg;

    @JsonProperty("result")
    private Result result;


    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("list")
        private List<Instrument> list;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Instrument {
        @JsonProperty("lotSizeFilter")
        private LotSizeFilter lotSizeFilter;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LotSizeFilter {

        /**
         * Шаг количества ордера (qtyStep).
         */
        @JsonProperty("qtyStep")
        private double qtyStep;

        /**
         * Минимально допустимое количество ордера (minOrderQty).
         */
        @JsonProperty("minOrderQty")
        private double minOrderQty;
    }
}
