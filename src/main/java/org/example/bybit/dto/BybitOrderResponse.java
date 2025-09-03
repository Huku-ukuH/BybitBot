package org.example.bybit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BybitOrderResponse {
    @JsonProperty("retCode")
    private int retCode;
    @JsonProperty("retMsg")
    private String retMsg;
    @JsonProperty("result")
    private OrderResult orderResult;
    @JsonProperty("retExtInfo")
    private Object retExtInfo; // Можно сделать отдельный класс, если потребуется
    @JsonProperty("time")
    private long time;

    @Getter
    @Setter
    public static class OrderResult {
        @JsonProperty("orderId")
        private String orderId;
        @JsonProperty("orderLinkId")
        private String orderLinkId;
    }

    public boolean isSuccess() {
        return "OK".equalsIgnoreCase(this.getRetMsg());
    }
    @Override
    public String toString() {
        return "BybitOrderResponse{" +
                "retCode=" + retCode +
                ", retMsg='" + retMsg + '\'' +
                ", orderId='" + (orderResult != null ? orderResult.orderId : "null") + '\'' +
                '}';
    }
}
