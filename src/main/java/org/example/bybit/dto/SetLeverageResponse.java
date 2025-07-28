package org.example.bybit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetLeverageResponse {
    @JsonProperty("retCode")
    private int retCode;
    @JsonProperty("retMsg")
    private String retMsg;
    @JsonProperty("result")
    private Result result;
    @JsonProperty("time")
    private long time;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("leverage")
        private String leverage;
    }
}
