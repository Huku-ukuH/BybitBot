package org.example.update;

import org.example.bybit.BybitManager;
import org.example.bybit.service.BybitPositionTrackerService;
import org.example.deal.Deal;
import org.example.deal.utils.OrderManager;
import org.example.model.Direction;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OrderRestorer {

    public String restoreOrders(Deal deal, BybitManager bybitManager) {

        BybitPositionTrackerService trackerService = bybitManager.getBybitPositionTrackerService();
        String symbol = deal.getSymbol().toString();
        StringBuilder result = new StringBuilder();

        try {
            List<BybitPositionTrackerService.OrderInfo> orders = trackerService.getOrders(symbol);
            if (orders == null || orders.isEmpty()) {
                return "📭 Нет активных ордеров для символа " + symbol;
            }


            //Возможна ситуация, когда на бирже нет ордера который есть у Deal
            //надо подумать в каких случаях это корректно, а в каких нет, пока оставлю

            LoggerUtils.info("📥 ОРДЕРА " + symbol + ": " + JsonUtils.toJson(orders));

            for (var order : orders) {
                if (!Boolean.TRUE.equals(order.getReduceOnly())) continue;

                // Уже привязан?
                if (isAlreadyBound(deal, order.getOrderId())) continue;

                if ("Stop".equals(order.getStopOrderType())) {
                    Double triggerPrice = parseDouble(order.getTriggerPrice());
                    if (triggerPrice == null) continue;

                    boolean isStopLoss = isStopLossPrice(triggerPrice, deal.getDirection(), deal.getEntryPrice());
                    OrderManager.OrderType type = isStopLoss ? OrderManager.OrderType.SL : OrderManager.OrderType.TP;
                    result.append(deal.addOrderId(new OrderManager(order.getOrderId(), type, triggerPrice)));

                } else if (order.getPrice() != null && !order.getPrice().isEmpty()) {
                    if (isTakeProfitOrder(order, deal.getDirection())) {
                        Double price = parseDouble(order.getPrice());
                        if (price != null) {
                            result.append(deal.addOrderId(new OrderManager(order.getOrderId(), OrderManager.OrderType.TP, price)));
                        }
                    }
                }
            }
        } catch (IOException e) {
            LoggerUtils.error("⚠️ Не удалось загрузить ордера с Bybit для символа " + symbol, e);
        }

        return result.toString();
    }

    private boolean isAlreadyBound(Deal deal, String orderId) {
        return deal.getOrdersIdList() != null &&
                deal.getOrdersIdList().stream()
                        .anyMatch(om -> Objects.equals(om.getOrderId(), orderId));
    }

    private boolean isStopLossPrice(double price, Direction direction, double entryPrice) {
        return direction == Direction.LONG ? price < entryPrice : price > entryPrice;
    }

    private boolean isTakeProfitOrder(BybitPositionTrackerService.OrderInfo order, Direction direction) {
        return (direction == Direction.LONG && "Sell".equals(order.getSide())) ||
                (direction == Direction.SHORT && "Buy".equals(order.getSide()));
    }

    /**
     * Возвращает только лимитные ордера (orderType = "Limit") по всем USDT-контрактам.
     * Используется для создания сделок по ожидающим входам.
     */
    public List<BybitPositionTrackerService.OrderInfo> getUsdtLimitOrders(Object bybitManager) throws IOException {
        BybitPositionTrackerService trackerService = ((BybitManager) bybitManager).getBybitPositionTrackerService();

        List<BybitPositionTrackerService.OrderInfo> allOrders = trackerService.getOrdersBySettleCoin("USDT");

        if (allOrders == null || allOrders.isEmpty()) {
            return List.of();
        }

        return allOrders.stream()
                .filter(order -> "Limit".equals(order.getOrderType()))
                .filter(order -> order.getSymbol() != null)
                .collect(Collectors.toList());
    }

    private Double parseDouble(String s) {
        try {
            return s != null ? Double.parseDouble(s.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}