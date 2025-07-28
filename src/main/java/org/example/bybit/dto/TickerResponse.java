package org.example.bybit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TickerResponse {
    @JsonProperty("retCode")
    private String retCode;
    @JsonProperty("retMsg")
    private String retMsg;
    @JsonProperty("result")
    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("category")
        private String category;
        @JsonProperty("list")
        private List<Ticker> list;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ticker {
        @JsonProperty("symbol")
        private String symbol;
        @JsonProperty("lastPrice")
        private String lastPrice;
    }
}
