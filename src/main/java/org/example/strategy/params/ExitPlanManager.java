package org.example.strategy.params;

import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.service.BybitOrderService;
import org.example.deal.Deal;
import org.example.deal.utils.DealCalculator;
import org.example.deal.utils.OrderManager;
import org.example.model.Direction;
import org.example.result.OperationResult;
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
     * Исполняет план выхода — устанавливает TP-ордера по шагам.
     * Всегда возвращает OperationResult.success() с детальным отчётом.
     * Ошибки не прерывают выполнение — все шаги обрабатываются.
     */
    public OperationResult executeExitPlan(Deal deal, ExitPlan plan) {
        if (plan == null || plan.getSteps().isEmpty()) {
            return OperationResult.failure("Нет плана выхода для выполнения.");
        }

        StringBuilder sb = new StringBuilder("Результат установки TP:\n");
        int totalSteps = plan.getSteps().size();
        int successfulSteps = 0;

        for (ExitPlan.ExitStep step : plan.getSteps()) {
            OperationResult stepResult = processTakeProfitStep(deal, step);
            sb.append(stepResult.getMessage()).append("\n");
            if (stepResult.isSuccess()) {
                successfulSteps++;
            }
        }

        // Формируем итоговое сообщение
        String finalMessage;
        if (successfulSteps == totalSteps) {
            finalMessage = "✅ Все TP успешно установлены.\n" + sb;
        } else if (successfulSteps > 0) {
            finalMessage = "⚠️ Частичный успех: установлено " + successfulSteps + " из " + totalSteps + " TP.\n" + sb;
        } else {
            finalMessage = "❌ Ни один TP не удалось установить.\n" + sb;
        }

        return OperationResult.success(finalMessage);
    }

    // --- Вспомогательные методы ---

    private OperationResult processTakeProfitStep(Deal deal, ExitPlan.ExitStep step) {
        double tpPrice = step.getTakeProfit();
        int percentage = step.getPercentage();
        double qty = dealCalculator.calculateExitQty(deal, percentage);

        if (isQuantityTooSmall(qty)) {
            return OperationResult.success(
                    "❌ TP " + String.format("%.2f", tpPrice) + ": объём < minQty — пропущен"
            );
        }

        try {
            BybitOrderResponse orderResponse = bybitOrderService.placeOrder(
                    BybitOrderRequest.forTakeProfit(deal, tpPrice, qty)
            );

            if (!orderResponse.isSuccess()) {
                return handleFailedOrderResponse(deal, tpPrice, orderResponse);
            }

            return handleSuccessfulOrderResponse(deal, tpPrice, percentage, qty, orderResponse);

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return OperationResult.success(
                    "❌ Ошибка TP " + MathUtils.formatPrice(deal.getEntryPrice(), tpPrice) + ": " + errorMsg
            );
        }
    }

    private boolean isQuantityTooSmall(double qty) {
        return qty == 0.0;
    }

    private OperationResult handleFailedOrderResponse(Deal deal, double tpPrice, BybitOrderResponse response) {
        String retMsg = response.getRetMsg();
        if (retMsg == null) retMsg = "Неизвестная ошибка от Bybit";

        if (isTriggerPriceError(retMsg)) {
            return OperationResult.success(
                    "⚠️ TP " + MathUtils.formatPrice(deal.getEntryPrice(), tpPrice) +
                            ": цена прошла уровень — ордер не установлен"
            );
        }

        return OperationResult.success(
                "❌ TP " + MathUtils.formatPrice(deal.getEntryPrice(), tpPrice) + ": " + retMsg
        );
    }

    private boolean isTriggerPriceError(String retMsg) {
        return retMsg.contains("trigger price") ||
                retMsg.contains("expect Rising") ||
                retMsg.contains("expect Falling") ||
                retMsg.contains("should be higher") ||
                retMsg.contains("should be lower");
    }

    private OperationResult handleSuccessfulOrderResponse(Deal deal, double tpPrice, int percentage, double qty,
                                                          BybitOrderResponse orderResponse) {
        OperationResult addResult = deal.addOrderId(
                new OrderManager(orderResponse.getOrderResult().getOrderId(), OrderManager.OrderType.TP, tpPrice)
        );

        if (!addResult.isSuccess()) {
            return OperationResult.success(
                    "⚠️ TP " + MathUtils.formatPrice(deal.getEntryPrice(), tpPrice) +
                            ": ордер установлен на бирже, но не сохранён локально!"
            );
        }

        double entryPrice = deal.getEntryPrice();
        double leverage = deal.getLeverageUsed();
        double basePnlPercent = (deal.getDirection() == Direction.LONG
                ? (tpPrice - entryPrice)
                : (entryPrice - tpPrice)) / entryPrice * 100;
        double leveragedPnl = basePnlPercent * leverage;

        String message = "✅ TP " +
                MathUtils.formatPrice(entryPrice, tpPrice) +
                " (+" + String.format("%.1f", leveragedPnl) + "%)" +
                " (" + percentage + "%, qty " + MathUtils.formatPrice(deal.getPositionSize(), qty) + ")";

        return OperationResult.success(message);
    }
}