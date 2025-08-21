package org.example.strategy.params;

import org.example.bot.MessageSender;
import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.service.BybitOrderService;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;

public class ExitPlanManager {

    private final DealCalculator dealCalculator;
    private final BybitOrderService bybitOrderService;
    private final MessageSender messageSender;

    public ExitPlanManager(
            DealCalculator dealCalculator,
            BybitOrderService bybitOrderService,
            MessageSender messageSender) {
        this.dealCalculator = dealCalculator;
        this.bybitOrderService = bybitOrderService;
        this.messageSender = messageSender;
    }

    /**
     * исполняет план
     */
    public void executeExitPlan(Deal deal, ExitPlan plan, long chatId) {
        if (plan == null || plan.getSteps().isEmpty()) {
            messageSender.send(chatId, "Нет плана выхода для выполнения.");
            return;
        }

        StringBuilder sb = new StringBuilder("Результат установки TP: ");
        for (ExitPlan.ExitStep step : plan.getSteps()) {
            double tpPrice = step.getTakeProfit();
            int percentage = step.getPercentage();
            double qty = dealCalculator.calculateExitQty(deal, percentage);

            if (qty == 0.0) {
                sb.append("❌ TP ").append(String.format("%.2f", tpPrice))
                        .append(": объём < minQty — пропущен ");
                continue;
            }

            try {
                bybitOrderService.placeOrder(new BybitOrderRequest(deal, tpPrice, qty));
                sb.append("✅ TP ").append(String.format("%.2f", tpPrice))
                        .append(" (").append(percentage).append("%, qty ").append(String.format("%.3f", qty)).append(") ");
            } catch (Exception e) {
                sb.append("❌ Ошибка TP ").append(String.format("%.2f", tpPrice))
                        .append(": ").append(e.getMessage()).append(" ");
            }
        }
        messageSender.send(chatId, sb.toString());
    }
}