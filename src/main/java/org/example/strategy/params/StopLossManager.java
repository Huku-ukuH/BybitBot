package org.example.strategy.params;

import org.example.deal.Deal;
import org.example.model.Direction;
import org.example.util.LoggerUtils;
import org.example.util.MathUtils;

/**
 * Универсальный менеджер Stop Loss.
 * Предоставляет методы для расчета и обновления SL,
 * но не содержит конкретной логики "когда и как" это делать.
 * Эта логика должна быть в стратегии.
 */
public class StopLossManager {

    /**
     * Перемещает SL сделки на новое значение.
     * @param deal Сделка для обновления.
     * @param newSl Новое значение SL.
     * @return true, если SL был успешно обновлён, false если новое значение хуже текущего.
     */

    public boolean moveStopLoss(Deal deal, double newSl) {
        newSl = MathUtils.formatPrice(newSl, deal.getStopLoss());

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
            LoggerUtils.debug("SL для сделки " + deal.getId() + " перемещён с " + oldSl + " на " + newSl);
            return true;
        } else {
            LoggerUtils.debug("Новое значение SL (" + newSl + ") хуже текущего (" + deal.getStopLoss() + ") для сделки " + deal.getId() + ". Игнорируется.");
            return false;
        }
    }



    /**
     * Перемещает SL на уровень предыдущего TP.
     * @param deal Сделка.
     * @param previousTp Уровень предыдущего TP.
     * @return Результат операции moveStopLoss.
     */
    public boolean moveStopLossToPreviousTP(Deal deal, double previousTp) {
        return moveStopLoss(deal, previousTp);
    }

    // ... другие универсальные методы, например, для трейлинг-стопа ...
}