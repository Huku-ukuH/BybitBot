package org.example.strategy.params;

import org.example.deal.Deal;
import org.example.deal.dto.PartialExitPlan;

public class ExitPlanManager {

    private final DealCalculator dealCalculator;
    private final BybitOrderService bybitOrderService;
    private final MessageSender messageSender;
    private final PartialExitPlanner partialExitPlanner;

    public ExitPlanManager(
            DealCalculator dealCalculator,
            BybitOrderService bybitOrderService,
            MessageSender messageSender,
            PartialExitPlanner partialExitPlanner) {
        this.dealCalculator = dealCalculator;
        this.bybitOrderService = bybitOrderService;
        this.messageSender = messageSender;
        this.partialExitPlanner = partialExitPlanner;
    }

    /**
     * Создаёт план выхода на основе сделки и её конфигурации.
     * Если TP есть — использует TP.
     * Если нет — использует PnL-правила.
     */
    public PartialExitPlan.ExitPlan createExitPlan(Deal deal) {
        StrategyConfig config = deal.getStrategy().getConfig();

        if (deal.getTakeProfits() != null && !deal.getTakeProfits().isEmpty()) {
            // План по TP
            PartialExitPlan plan = partialExitPlanner.planExit(deal.getTakeProfits(), config.getTpExitRules());
            return ExitPlan.fromTpSteps(plan.getPartialExits());
        } else {
            // План по PnL
            return ExitPlan.fromPnlLevels(config.getPnlTpExitRules());
        }
    }

    /**
     * Выполняет план выхода: выставляет ордера.
     * Использует DealCalculator для проверки размеров.
     */
    public void executeExitPlan(Deal deal, ExitPlan plan, long chatId) {
        if (plan == null || plan.getSteps().isEmpty()) {
            messageSender.send(chatId, "Нет плана выхода для выполнения.");
            return;
        }

        StringBuilder sb = new StringBuilder("Результат установки TP:\n");

        for (ExitStep step : plan.getSteps()) {
            double tpPrice = step.getTakeProfit();
            int percentage = step.getPercentage();

            // ✅ Используем ваш существующий DealCalculator
            double qty = dealCalculator.calculateExitQty(deal, percentage);
            if (qty == 0.0) {
                sb.append(EmojiUtils.CROSS)
                        .append(" TP ").append(String.format("%.2f", tpPrice))
                        .append(": объём < minQty — пропущен\n");
                continue;
            }

            try {
                bybitOrderService.placeLimitExitOrder(deal, tpPrice, qty);
                sb.append(EmojiUtils.OKAY)
                        .append(" TP ").append(String.format("%.2f", tpPrice))
                        .append(" (").append(percentage).append("%, qty ").append(String.format("%.3f", qty)).append(")\n");
            } catch (Exception e) {
                sb.append(EmojiUtils.CROSS)
                        .append(" Ошибка TP ").append(String.format("%.2f", tpPrice))
                        .append(": ").append(e.getMessage()).append("\n");
            }
        }

        if (sb.length() > 0) {
            messageSender.send(chatId, sb.toString());
        }
    }
}