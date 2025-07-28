
package org.example.strategy;

import org.example.bybit.dto.TickerResponse;
import org.example.deal.Deal;
import org.example.deal.dto.PartialExitPlan;
import org.example.model.Direction;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;
import org.example.util.LoggerUtils;
import org.example.strategy.params.PartialExitPlanner;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Базовая стратегия, реализующая стандартную логику управления сделкой.
 */
public class BasedStrategy implements TradingStrategy {

    private final StrategyConfig config = new StrategyConfig();
    // Простое множество для отслеживания уже сработавших уровней PnL
    private final Set<Double> triggeredPnlLevels = new HashSet<>();

    @Override
    public StrategyConfig getConfig() {
        return this.config;
    }

    @Override
    public List<PartialExitPlan> planExit(StrategyContext context) throws StrategyException {
        Deal deal = context.getActiveDeal();
        if (deal == null || deal.getTakeProfits().isEmpty()) {
            LoggerUtils.logWarn("BasedStrategy: Нет активной сделки или TP для планирования выхода.");
            return Collections.emptyList();
        }

        StrategyConfig config = this.getConfig();
        Map<Integer, int[]> rules = config.getTpExitRules();

        PartialExitPlanner planner = new PartialExitPlanner();
        PartialExitPlan plan = planner.planExit(deal.getTakeProfits(), rules);

        if (plan != null) {
            LoggerUtils.logInfo("BasedStrategy: Создан план выхода с " + plan.getPartialExits().size() + " шагами.");
            return Collections.singletonList(plan);
        } else {
            LoggerUtils.logWarn("BasedStrategy: PartialExitPlanner не смог создать план.");
            return Collections.emptyList();
        }
    }

    @Override
    public void onPriceUpdate(StrategyContext context, TickerResponse price) {
        Deal deal = context.getActiveDeal();
        if (deal == null || !deal.isActive()) {
            return;
        }
        if (price.getResult() == null || price.getResult().getList() == null || price.getResult().getList().isEmpty()) {
            LoggerUtils.logWarn("onPriceUpdate: Получен пустой TickerResponse для сделки " + deal.getId());
            return;
        }

        String dealSymbol = deal.getSymbol().toString();

        Double currentPrice = null;
        for (TickerResponse.Ticker ticker : price.getResult().getList()) {
            if (dealSymbol.equals(ticker.getSymbol())) {
                try {
                    currentPrice = Double.parseDouble(ticker.getLastPrice());
                    break;
                } catch (NumberFormatException e) {
                    LoggerUtils.logError("onPriceUpdate: Ошибка парсинга цены для " + ticker.getSymbol(), e);
                }
            }
        }

        if (currentPrice == null) {
            LoggerUtils.logWarn("onPriceUpdate: Цена для " + dealSymbol + " не найдена.");
            return;
        }

        double entryPrice = deal.getEntryPrice();
        Direction direction = deal.getDirection();

        if (entryPrice <= 0) {
            LoggerUtils.logWarn("onPriceUpdate: Некорректная цена входа для сделки " + deal.getId());
            return;
        }

        double pnlPercent;
        if (direction == Direction.LONG) {
            pnlPercent = ((currentPrice - entryPrice) / entryPrice) * 100.0 * deal.getLeverageUsed();
        } else {
            pnlPercent = ((entryPrice - currentPrice) / entryPrice) * 100.0 * deal.getLeverageUsed();
        }

        LoggerUtils.logDebug("BasedStrategy (" + deal.getId() + "): PnL = " + String.format("%.2f", pnlPercent) + "%");

        // Получаем правила выхода по PnL из конфига стратегии
        Map<Double, Integer> pnlRules = config.getPnlTpExitRules();
        if (pnlRules.isEmpty()) {
            LoggerUtils.logDebug("BasedStrategy: Нет правил выхода по PnL в конфиге.");
            return;
        }

        // Проверяем, достигнуты ли уровни PnL
        for (Map.Entry<Double, Integer> ruleEntry : pnlRules.entrySet()) {
            double targetPnlLevel = ruleEntry.getKey() * deal.getLeverageUsed();
            int exitPercentage = ruleEntry.getValue() * deal.getLeverageUsed();

            boolean levelReached = (direction == Direction.LONG && pnlPercent >= targetPnlLevel) ||
                    (direction == Direction.SHORT && pnlPercent >= targetPnlLevel);

            if (levelReached && !triggeredPnlLevels.contains(targetPnlLevel)) {
                deal.addTakeProfit(currentPrice);
                triggeredPnlLevels.add(targetPnlLevel);
                LoggerUtils.logInfo("BasedStrategy: Достигнут PnL " + String.format("%.2f", targetPnlLevel) +
                        "%. Установлен TP. Планируется выход " + exitPercentage + "% позиции.");



                // TODO: Здесь должна быть логика фактического размещения TP-ордера с exitPercentage
                // Например, вызов BybitOrderService.placeTakeProfitOrder с рассчитанным qty
            }
        }
    }

    @Override
    public void onTakeProfitHit(StrategyContext context, double executedPrice) {
        LoggerUtils.logInfo("BasedStrategy: Сработал TP на уровне " + executedPrice + ".");
        // Сброс триггера, если нужно повторно реагировать на тот же уровень (обычно не нужно)
        // triggeredPnlLevels.removeIf(level -> Math.abs(level - ...) < epsilon);
    }

    @Override
    public void onStopLossHit(StrategyContext context) {
        LoggerUtils.logWarn("BasedStrategy: Сработал SL.");
        // Очищаем отслеживаемые уровни при закрытии сделки
        triggeredPnlLevels.clear();
    }
}