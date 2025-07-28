package org.example.bybit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitOrderListResponse {

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

        @JsonProperty("list")
        private List<Order> list;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Order {

        @JsonProperty("orderId")
        private String orderId;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("side")
        private String side;

        @JsonProperty("orderType")
        private String orderType;

        @JsonProperty("reduceOnly")
        private boolean reduceOnly;

        @JsonProperty("price")
        private String price;

        @JsonProperty("qty")
        private String qty;

        @JsonProperty("triggerPrice")
        private String triggerPrice;

        @JsonProperty("triggerDirection")
        private Integer triggerDirection;

        @JsonProperty("orderStatus")
        private String orderStatus;
    }
}
