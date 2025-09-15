package org.example.strategy.strategies.strategies;

import org.example.ai.AiService;
import org.example.bybit.BybitManager;
import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.dto.TickerResponse;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.bybit.service.BybitOrderService;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.deal.DealValidator;
import org.example.deal.dto.DealRequest;
import org.example.deal.dto.DealValidationResult;
import org.example.model.EntryType;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.params.ExitPlan;
import org.example.model.Direction;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;
import org.example.strategy.params.ExitPlanManager;
import org.example.util.LoggerUtils;
import org.example.strategy.params.PartialExitPlanner;
import org.example.util.ValuesUtil;

import java.util.*;

/**
 * Абстрактная базовая стратегия, реализующая общую логику управления сделкой.
 * Конкретные стратегии (например, AiStrategy, MartingaleStrategy) должны наследоваться от этого класса
 * и реализовывать абстрактные методы.
 */
public abstract class AbstractStrategy implements TradingStrategy {

    protected StrategyConfig config;
    protected final Set<Double> triggeredPnlLevels = new HashSet<>();
    public AbstractStrategy() {
        this.config = createConfig();
        LoggerUtils.logDebug(getClass().getSimpleName() + ": Инициализирована с конфигом: " + config);
    }
    protected StrategyConfig createConfig() {
        return new StrategyConfig();
    }

    /**
     * метод создания сделки,
     * @param aiService может быть null, если deal создается НЕ из handleGetSignal"
     */
    @Override
    public Deal createDeal(AiService aiService, String messageText, long chatId, String strategyName) {
        LoggerUtils.logDebug("Создание сделки по сигналу: " + messageText);
        try {
            DealRequest request = aiService.parseSignal(messageText);
            Deal deal = new Deal(request);
            deal.setChatId(chatId);
            deal.setStrategyName(strategyName);
            return deal;
        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось создать сделку по сигналу: " + messageText, e);
            throw e;
        }
    }

    @Override
    public Deal createDeal(PositionInfo positionInfo, long chatId, String strategyName) {
        LoggerUtils.logDebug("Создание сделки по существующей позиции: ");
        DealRequest request = new DealRequest();
        Deal deal = null;
        try {
            request.setSymbol(positionInfo.getSymbol());
        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось присвоить тикер для dealRequest. (см совместимость типов в dealRequest) ", e);
            throw e;
        }
        try {
            request.setDirection(positionInfo.getSide());
        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось присвоить направление для dealRequest. (см совместимость типов в dealRequest) ", e);
            throw e;
        }
        try {
            request.setEntryType(EntryType.MARKET);
            request.setEntryPrice(positionInfo.getAvgPrice());
            request.setStopLoss(positionInfo.getStopLoss());

        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось присвоить ТВХ, SL или тип входа сделку для dealRequest. (см совместимость типов в dealRequest) ", e);
            throw e;
        }
        try {
            request.setTakeProfits(new ArrayList<>() {
            });
        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось присвоить TP для dealRequest. (см совместимость типов в dealRequest) ", e);
            throw e;
        }
        try {
            deal = new Deal(request);
            deal.setChatId(chatId);
            deal.setStrategyName(strategyName);
            deal.setPositionInfo(positionInfo);
            deal.updateDealFromBybitPosition(positionInfo);
        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось присвоить сделкe dealRequest ", e);
        }
        return deal;
    }

    public DealValidationResult validateDeal(Deal deal, BybitMarketService marketService) { return new DealValidator().validate(deal, marketService); }
    public String calculateDeal (Deal deal, DealCalculator dealCalculator) {
        return dealCalculator.calculate(deal);
    }



    public boolean openDeal(BybitOrderService bybitOrderService, Deal deal) {
        // Этап 1: Установка плеча
        try {
            bybitOrderService.setLeverage(deal);
        } catch (Exception e) {
            throw new RuntimeException("❌ Ошибка при установке плеча для символа " + deal.getSymbol(), e);
        }

        // Этап 2: Выставление ордера
        try {
            BybitOrderRequest request = BybitOrderRequest.forEntry(deal);
            BybitOrderResponse orderResponse = bybitOrderService.placeOrder(request);

            // Логируем ответ от Bybit, даже если всё ок
            String retMsg = orderResponse.getRetMsg();
            String fullMessage = retMsg != null ? retMsg : "No message from Bybit";

            if (orderResponse.isSuccess()) {
                deal.setId(orderResponse.getOrderResult().getOrderId());
                return true;
            } else {
                LoggerUtils.logWarn("Ордер не размещён для " + deal.getSymbol() + ": " + fullMessage);
                return false;
            }
        } catch (Exception e) {
            // Ловим исключение до того, как retMsg будет доступен
            throw new RuntimeException("❌ Ошибка при выставлении ордера для символа " + deal.getSymbol(), e);
        }
    }
    public String goIfDealOpen(Deal deal, BybitManager bybitManager) {
        return setSL(deal, bybitManager) + "\n" + setTP(deal, bybitManager);
    }

    public String setSL(Deal deal, BybitManager bybitManager){
        // Устанавливаем стоп-лосс
        String result;
        try {
            BybitOrderResponse slResponse = bybitManager.getBybitOrderService().setStopLoss(deal);
            String retMsg = slResponse.getRetMsg();

            if (!slResponse.isSuccess()) {
                result = retMsg != null ? retMsg : "No error message from Bybit";
                throw new IllegalStateException("❌ Не удалось установить SL: " + result);
            }

            result = "✅ Стоп-лосс установлен для " + deal.getSymbol() + ": " + deal.getStopLoss();
            LoggerUtils.logInfo(result);
        } catch (Exception e) {
            throw new RuntimeException("❌ Ошибка при установке SL для символа " + deal.getSymbol(), e);
        }
        return result;
    }
    public String setTP(Deal deal, BybitManager bybitManager) {
        // Устанавливаем TP через ExitPlan
        try {
            deal.setActive(true);
            ExitPlan plan = deal.getStrategy().planExit(deal);

            if (plan == null || plan.getSteps().isEmpty()) {
                return "⚠️ План выхода не сформирован.";
            }

            ExitPlanManager exitPlanManager = new ExitPlanManager(
                    new DealCalculator(bybitManager.getBybitAccountService(), bybitManager.getBybitMarketService()),
                    bybitManager.getBybitOrderService()
            );

            return exitPlanManager.executeExitPlan(deal, plan);
        } catch (Exception e) {
            LoggerUtils.logError("❌ Ошибка при установке TP для символа " + deal.getSymbol(), e);
            throw new RuntimeException("❌ Ошибка при установке TP для символа " + deal.getSymbol(), e);
        }
    }
    public ExitPlan planExit(Deal deal) {
        try {
            LoggerUtils.logInfo("🔍 " + getClass().getSimpleName() + ": Начало сделки " + deal.getId());

            StrategyConfig config = this.getConfig();
            double entryPrice = deal.getEntryPrice();
            Direction direction = deal.getDirection();

            // 1. Попытка по TP
            if (deal.getTakeProfits() != null && !deal.getTakeProfits().isEmpty()) {
                List<ExitPlan.ExitStep> steps = new PartialExitPlanner()
                        .planExit(deal.getTakeProfits(), config.getTpExitRules());
                if (!steps.isEmpty()) {
                    return new ExitPlan(steps, ExitPlan.ExitType.TP);
                }
            }

            // 2. Попытка по PnL
            Map<Double, Integer> pnlRules = config.getPnlTpExitRules();
            if (pnlRules != null && !pnlRules.isEmpty()) {
                LoggerUtils.logInfo("📈 PnL-правила: " + pnlRules);
                LoggerUtils.logInfo("➤ Вызываю ExitPlan.fromPnl() для создания плана по PnL");

                // 🔥 Здесь происходит NoSuchMethodError
                ExitPlan plan = ExitPlan.fromPnl(pnlRules, entryPrice, direction);

                if (plan != null && !plan.getSteps().isEmpty()) {
                    LoggerUtils.logInfo("✅ План по PnL создан");
                    return plan;
                }
            }

            LoggerUtils.logWarn("⚠️ Не удалось создать план выхода");
            return null;

        } catch (Error err) {
            // ✅ Ловим NoSuchMethodError
            LoggerUtils.logError("🔴 FATAL: Ошибка выполнения (возможно, метод не найден)", err);
            return null;
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка в planExit()", e);
            e.printStackTrace();
            return null;
        }
    }
    public double RiskUpdate(BybitAccountService bybitAccountService) {
        double updateLoss = bybitAccountService.getUsdtBalance() / 100 * ValuesUtil.getDefaultLossPrecent();
        this.config = new StrategyConfig(
                null,
                updateLoss,
                new int[]{5, 10, 20},
                15.0,
                null,
                null
        );
        return updateLoss;
    }




    @Override
    public StrategyConfig getConfig() {
        return config;
    }
    @Override
    public void onPriceUpdate(StrategyContext context, TickerResponse price) {
        Deal deal = context.getActiveDeal();
        if (deal == null || !deal.isActive()) {
            return;
        }
        if (price.getResult() == null || price.getResult().getList() == null || price.getResult().getList().isEmpty()) {
            LoggerUtils.logWarn(getClass().getSimpleName() + " onPriceUpdate: Получен пустой TickerResponse для сделки " + deal.getId());
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
                // deal.addTakeProfit(currentPrice);
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
        LoggerUtils.logInfo(getClass().getSimpleName() + ": Сработал TP на уровне " + executedPrice + ".");
    }
    @Override
    public void onStopLossHit(StrategyContext context) {
        LoggerUtils.logWarn(getClass().getSimpleName() + ": Сработал SL.");
        triggeredPnlLevels.clear();
    }



}