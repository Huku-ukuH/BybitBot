package org.example.strategy.params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.model.Direction;
import org.example.util.LoggerUtils;
import org.example.util.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class ExitPlan {
    public enum ExitType {
        TP,
        PNL,
        TRAILING // по трейлингу
    }

    private final List<ExitStep> steps;
    private final ExitType type;

    public ExitPlan(List<ExitStep> steps, ExitType type) {
        this.steps = new ArrayList<>(steps);
        this.type = type;
    }

    /**
     * Создаёт план выхода по уровням TP.
     */
    public static ExitPlan fromTp(List<Double> takeProfits, Map<Integer, int[]> rules) {
        if (takeProfits == null || takeProfits.isEmpty()) {
            return null;
        }

        int count = takeProfits.size();
        int[] distribution = rules.get(count);
        if (distribution == null || distribution.length != count) {
            return null;
        }

        List<ExitStep> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(new ExitStep(takeProfits.get(i), distribution[i]));
        }

        return new ExitPlan(steps, ExitType.TP);
    }

    /**
     * Создаёт план выхода по уровням PnL.
     * PnL-уровни — это проценты прибыли, при достижении которых нужно выйти.
     */
    public static ExitPlan fromPnl(Map<Double, Integer> pnlRules, double entryPrice, Direction direction)  {
        LoggerUtils.logInfo("📊 ExitPlan.fromPnl(): Начало создания плана по PnL");
        LoggerUtils.logInfo("  ➤ Цена входа: " + entryPrice +
                "\n➤ Направление: \" + direction)" +
                "\n➤ Количество PnL-уровней: " + pnlRules.size());

        List<ExitStep> steps = new ArrayList<>();

        for (Map.Entry<Double, Integer> entry : pnlRules.entrySet()) {
            double pnlPercent = entry.getKey();
            int percentage = entry.getValue();
            double targetPrice;

            if (direction == Direction.LONG) {
                targetPrice = entryPrice * (1 + pnlPercent / 100.0);                              //возможное место ошибок
                LoggerUtils.logInfo("  ➤ " + pnlPercent + "% → цена = " + entryPrice + " * (1 + " + (pnlPercent / 100.0) + ") = " + MathUtils.formatPrice(entryPrice, targetPrice));
            } else {
                targetPrice = entryPrice * (1 - pnlPercent / 100.0);                              //возможное место ошибок
                LoggerUtils.logInfo("  ➤ " + pnlPercent + "% → цена = " + entryPrice + " * (1 - " + (pnlPercent / 100.0) + ") = " + MathUtils.formatPrice(entryPrice, targetPrice));
            }

            // Защита от некорректной цены
            if (targetPrice <= 0) {
                LoggerUtils.logWarn("❌❌❌❌❌ fromPnl(): Рассчитанная цена <= 0: " + targetPrice + " (пропускаем уровень)❌❌❌❌❌");
                continue;
            }

            steps.add(new ExitStep(targetPrice, percentage));
        }

        if (steps.isEmpty()) {
            LoggerUtils.logWarn("⚠️ ExitPlan.fromPnl(): Все уровни были отфильтрованы — возвращаем null");
            return null;
        }

        LoggerUtils.logInfo("✅ ExitPlan.fromPnl(): План создан с " + steps.size() + " шагами");
        return new ExitPlan(steps, ExitType.PNL);
    }
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor

    public static class ExitStep {
        private double takeProfit;
        private int percentage;

        @Override
        public String toString() {
            return "ExitStep{" +
                    "takeProfit=" + takeProfit +
                    ", percentage=" + percentage +
                    '}';
        }
    }

}