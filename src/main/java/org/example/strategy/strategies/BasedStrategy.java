
package org.example.strategy.strategies;

import org.example.bybit.dto.TickerResponse;
import org.example.bybit.service.BybitAccountService;
import org.example.deal.Deal;
import org.example.strategy.params.ExitPlan;
import org.example.model.Direction;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;
import org.example.util.LoggerUtils;
import org.example.strategy.params.PartialExitPlanner;
import org.example.util.ValuesUtil;

import java.util.*;

/**
 * Базовая стратегия, реализующая стандартную логику управления сделкой.
 */
public class BasedStrategy implements TradingStrategy {
    private StrategyConfig config;

    public BasedStrategy() {
        this.config = createConfig();
    }
    protected StrategyConfig createConfig() {
        return new StrategyConfig(
                null,
                null,
                new int[]{10, 20, 5},
                null,
                null,
                null
        );
    }

    private final Set<Double> triggeredPnlLevels = new HashSet<>();

    @Override
    public StrategyConfig getConfig() {
        return this.config;
    }

    @Override
    public ExitPlan planExit(Deal deal) {
        if (deal == null) {
            LoggerUtils.logWarn("BasedStrategy: Сделка null.");
            return null;
        }

        StrategyConfig config = this.getConfig();

        // 1. Сначала пробуем создать план по TP
        if (deal.getTakeProfits() != null && !deal.getTakeProfits().isEmpty()) {
            List<ExitPlan.ExitStep> steps = new PartialExitPlanner()
                    .planExit(deal.getTakeProfits(), config.getTpExitRules());

            if (!steps.isEmpty()) {
                LoggerUtils.logInfo("BasedStrategy: План выхода по TP создан с " + steps.size() + " шагами.");
                return new ExitPlan(steps, ExitPlan.ExitType.TP);
            }
        }

        // 2. Если TP нет или не удалось создать — пробуем PnL
        Map<Double, Integer> pnlRules = config.getPnlTpExitRules();
        if (pnlRules != null && !pnlRules.isEmpty()) {
            ExitPlan plan = ExitPlan.fromPnl(pnlRules, deal.getEntryPrice());
            if (!plan.getSteps().isEmpty()) {
                LoggerUtils.logInfo("BasedStrategy: План выхода по PnL создан.");
                return plan;
            }
        }

        // 3. Ничего не подошло
        LoggerUtils.logWarn("BasedStrategy: Не удалось создать план выхода — нет TP и нет PnL-правил.");
        return null;
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
            double exitPercentage = ruleEntry.getValue() * deal.getLeverageUsed();

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
    @Override
    public double lossUpdate(BybitAccountService bybitAccountService) {
        double updateLoss = bybitAccountService.getUsdtBalance()/100 * ValuesUtil.getDefaultLossPrecent();
        config = new StrategyConfig(null, updateLoss, new int[]{5, 10, 20}, 15.0, null, null
        );
        return updateLoss;
    }
}