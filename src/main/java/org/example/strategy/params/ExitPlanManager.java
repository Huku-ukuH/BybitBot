package org.example.strategy.params;

import org.example.bot.MessageSender;
import org.example.bybit.dto.BalanceResponse;
import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.service.BybitOrderService;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.deal.OrderManager;
import org.example.model.Direction;
import org.example.util.MathUtils;

public class ExitPlanManager {

    private final DealCalculator dealCalculator;
    private final BybitOrderService bybitOrderService;

    public ExitPlanManager(
            DealCalculator dealCalculator,
            BybitOrderService bybitOrderService) {
        this.dealCalculator = dealCalculator;
        this.bybitOrderService = bybitOrderService;
    }

    /**
     * исполняет план
     */
    public String executeExitPlan(Deal deal, ExitPlan plan) {
        if (plan == null || plan.getSteps().isEmpty()) {
            return "Нет плана выхода для выполнения.";
        }

        StringBuilder sb = new StringBuilder("Результат установки TP: ");
        for (ExitPlan.ExitStep step : plan.getSteps()) {
            double tpPrice = step.getTakeProfit();
            int percentage = step.getPercentage();
            double qty = dealCalculator.calculateExitQty(deal, percentage);

            if (qty == 0.0) {
                sb.append("\n❌ TP ").append(String.format("%.2f", tpPrice))
                        .append(": объём < minQty — пропущен ");
                continue;
            }

            try {
                BybitOrderResponse orderResponse = bybitOrderService.placeOrder(
                        BybitOrderRequest.forTakeProfit(deal, tpPrice, qty)
                );

                deal.addOrderId(new OrderManager(orderResponse.getOrderResult().getOrderId(), OrderManager.OrderType.TP, tpPrice));

                double entryPrice = deal.getEntryPrice();
                double leverage = deal.getLeverageUsed();

                // Расчёт базового PnL в %
                double basePnlPercent = (deal.getDirection() == Direction.LONG
                        ? (tpPrice - entryPrice)
                        : (entryPrice - tpPrice)) / entryPrice * 100;

                // С учётом плеча
                double leveragedPnl = basePnlPercent * leverage;

                sb.append("\n✅ TP ")
                        .append(MathUtils.formatPrice(entryPrice, tpPrice))
                        .append(" (+")
                        .append(String.format("%.1f", leveragedPnl)) // Один знак после запятой
                        .append("%)")
                        .append(" (").append(percentage).append("%, qty ")
                        .append(MathUtils.formatPrice(deal.getPositionSize(), qty)).append(") ");

            } catch (Exception e) {
                sb.append("❌ Ошибка TP ")
                        .append(MathUtils.formatPrice(deal.getEntryPrice(), tpPrice))
                        .append(": ").append(e.getMessage()).append(" ");
            }
        }
        return sb.toString();
    }
}