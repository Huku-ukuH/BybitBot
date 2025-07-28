package org.example.bybit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceResponse {
    private int retCode;
    @JsonProperty("retMsg")
    private String retMsg;
    @JsonProperty("result")
    private BalanceResult result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BalanceResult {
        @JsonProperty("list")
        private List<BalanceAccount> list;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BalanceAccount {
        private String accountType;
        @JsonProperty("totalEquity")
        private String totalEquity;
        @JsonProperty("totalAvailableBalance")
        private String totalAvailableBalance;
        @JsonProperty("coin")
        private List<CoinBalance> coin;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoinBalance {
        @JsonProperty("coin")
        private String coin;
        @JsonProperty("equity")
        private String equity;
        @JsonProperty("walletBalance")
        private String walletBalance;
        @JsonProperty("totalAvailableBalance")
        private String totalAvailableBalance;
    }
}