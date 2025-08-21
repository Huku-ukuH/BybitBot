//package org.example.monitor.trailing;
//
//import org.example.util.LoggerUtils;
//
//public class TrailingSession{
//    private enum State {
//        WAITING_FOR_PROFIT,
//        TRAILING_LOOSE,
//        TRAILING_TIGHT,
//        EXITED
//    }
//
//    private State state = State.WAITING_FOR_PROFIT;
//    private double highestPriceInProfit = Double.MIN_VALUE;
//    private final double looseTrailPct = 3.0;
//    private final double tightTrailPct = 1.5;
//    private final double tightenThreshold = 6.0; // Ужимаем при 6% прибыли
//
//
//    public void update(double currentPrice) {
//        if (state == State.EXITED) return;
//
//        // Обновляем максимум в прибыли
//        if (isInProfit(currentPrice)) {
//            highestPriceInProfit = Math.max(highestPriceInProfit, currentPrice);
//        }
//
//        switch (state) {
//            case WAITING_FOR_PROFIT:
//                if (isInProfit(currentPrice)) {
//                    state = State.TRAILING_LOOSE;
//                    LoggerUtils.logInfo("Trailing: активирован в режиме TRAILING_LOOSE");
//                }
//                break;
//
//            case TRAILING_LOOSE:
//                double looseTrigger = highestPriceInProfit * (1 - looseTrailPct / 100);
//                if (currentPrice <= looseTrigger) {
//                    // Пока не срабатываем — только следим
//                }
//
//                // Проверяем, пора ли ужать
//                double currentPnl = calculatePnlPercent(currentPrice);
//                if (currentPnl >= tightenThreshold) {
//                    state = State.TRAILING_TIGHT;
//                    LoggerUtils.logInfo("Trailing: усилен до TRAILING_TIGHT (PnL >= " + tightenThreshold + "%)");
//                }
//                break;
//
//            case TRAILING_TIGHT:
//                double tightTrigger = highestPriceInProfit * (1 - tightTrailPct / 100);
//                if (currentPrice <= tightTrigger) {
//                    // Сработало! Фиксируем часть
//                    orderService.placePartialExit(deal, exitPercentage, "Trailing TP (tight)");
//                    state = State.EXITED;
//                    stop();
//                }
//                break;
//        }
//    }
//}