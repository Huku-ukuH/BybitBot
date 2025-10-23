package org.example.deal.utils;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class OrderManager {
    // Класс имитирующий ордер в сделке

    private String orderId;
    private OrderType orderType;
    private double orderPrice; //значение для примерного понимания, как правило не соответствует цене по факту, но очень близко к ней

    @Override
    public String toString() {
        return "orderId='" + orderId +
                "\norderType=" + orderType +
                "\norderPrice=" + orderPrice;
    }

    public enum OrderType {
        ENTRY,
        TP,
        SL,
        LIMIT,
        TRAILING_TP,
        TRAILING_ENTRY
    }
}