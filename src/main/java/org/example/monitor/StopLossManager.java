// org.example.monitor.StopLossManager.java (переработанный)
package org.example.monitor;

import org.example.deal.Deal;
import org.example.model.Direction;
import org.example.util.MathUtils;
import org.example.util.LoggerUtils;

/**
 * Универсальный менеджер Stop Loss.
 * Предоставляет методы для расчета и обновления SL,
 * но не содержит конкретной логики "когда и как" это делать.
 * Эта логика должна быть в стратегии.
 */
public class StopLossManager {

    /**
     * Рассчитывает значение SL на основе цены входа и процента отклонения.
     * @param entryPrice Цена входа.
     * @param direction Направление сделки.
     * @param slPercent Процент для расчета SL (например, 0.1 для 0.1%).
     * @return Рассчитанное значение SL.
     */
    public static double calculateDefaultSL(double entryPrice, Direction direction, double slPercent) {
        if (direction == Direction.LONG) {
            return entryPrice * (1 - (slPercent / 100.0));
        } else { // SHORT
            return entryPrice * (1 + (slPercent / 100.0));
        }
    }

    /**
     * Перемещает SL сделки на новое значение.
     * @param deal Сделка для обновления.
     * @param newSl Новое значение SL.
     * @return true, если SL был успешно обновлён, false если новое значение хуже текущего.
     */
    public static boolean moveStopLoss(Deal deal, double newSl) {
        // Округляем до 2 знаков (или можно передавать точность как параметр)
        newSl = MathUtils.round(newSl, 2);

        // Проверяем, что новый SL лучше текущего
        boolean isBetter = false;
        if (deal.getDirection() == Direction.LONG) {
            isBetter = (deal.getStopLoss() == null) || (newSl > deal.getStopLoss());
        } else { // SHORT
            isBetter = (deal.getStopLoss() == null) || (newSl < deal.getStopLoss());
        }

        if (isBetter) {
            double oldSl = deal.getStopLoss();
            deal.setStopLoss(newSl);
            LoggerUtils.logDebug("SL для сделки " + deal.getId() + " перемещён с " + oldSl + " на " + newSl);
            return true;
        } else {
            LoggerUtils.logDebug("Новое значение SL (" + newSl + ") хуже текущего (" + deal.getStopLoss() + ") для сделки " + deal.getId() + ". Игнорируется.");
            return false;
        }
    }

    /**
     * Перемещает SL на уровень предыдущего TP.
     * @param deal Сделка.
     * @param previousTp Уровень предыдущего TP.
     * @return Результат операции moveStopLoss.
     */
    public static boolean moveStopLossToPreviousTP(Deal deal, double previousTp) {
        return moveStopLoss(deal, previousTp);
    }

    // ... другие универсальные методы, например, для трейлинг-стопа ...
}