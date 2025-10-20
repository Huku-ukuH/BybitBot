package org.example.strategy.strategies.strategies.superStrategy;

import org.example.ai.AiService;
import org.example.bybit.BybitManager;
import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.bybit.service.BybitOrderService;
import org.example.bybit.service.BybitPositionTrackerService;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.deal.DealValidator;
import org.example.model.Symbol;
import org.example.strategy.strategies.strategies.TradingStrategy;
import org.example.update.UpdateManager;
import org.example.deal.dto.DealRequest;
import org.example.deal.dto.DealValidationResult;
import org.example.model.EntryType;
import org.example.monitor.dto.PositionInfo;
import org.example.monitor.dto.PriceUpdate;
import org.example.strategy.params.ExitPlan;
import org.example.model.Direction;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.params.ExitPlanManager;
import org.example.strategy.params.StopLossManager;
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
    public AbstractStrategy() {
        this.config = createConfig();
        LoggerUtils.debug(getClass().getSimpleName() + ": Инициализирована с конфигом: " + config);
    }
    protected StrategyConfig createConfig() {
        return new StrategyConfig();
    }

    /**
     * метод создания сделки,
     * @param aiService может быть null, если deal создается НЕ из handleGetSignal"
     */
    @Override
    public Deal createDealBySignal(AiService aiService, String messageText, long chatId, String strategyName) {
        LoggerUtils.debug("Создание сделки по сигналу: " + messageText);
        try {
            DealRequest request = aiService.parseSignal(messageText);
            Deal deal = new Deal(request);
            deal.setChatId(chatId);
            deal.setStrategyName(strategyName);
            return deal;
        } catch (Exception e) {
            LoggerUtils.error("❌ Не удалось создать сделку по сигналу: " + messageText, e);
            throw e;
        }
    }

    public Deal createDealByUpdate(PositionInfo positionInfo, long chatId, String strategyName) {
        LoggerUtils.debug("Создание сделки по существующей позиции: " + positionInfo.getSymbol());

        DealRequest request = new DealRequest();
        request.setSymbol(new Symbol(positionInfo.getSymbol()));
        request.setDirection(positionInfo.getSide());
        request.setEntryType(EntryType.MARKET);
        request.setEntryPrice(positionInfo.getAvgPrice());
        request.setTakeProfits(new ArrayList<>());

        Deal deal = new Deal(request);
        deal.setChatId(chatId);
        deal.setStrategyName(strategyName);
        deal.setPositionSize(positionInfo.getSize());
        deal.setActive(true);

        LoggerUtils.debug("Создана новая сделка по позиции!" + deal.getSymbol().toString());
        return deal;
    }
    public Deal createDealByUpdate(BybitPositionTrackerService.OrderInfo limitOrder, long chatId, String strategyName) {
        LoggerUtils.debug("Создание сделки по лимитному ордеру: " + limitOrder.getSymbol());

        DealRequest request = new DealRequest();
        request.setSymbol(limitOrder.getSymbol());
        request.setDirection(Direction.fromString(limitOrder.getSide()));
        request.setEntryType(EntryType.MARKET);
        request.setEntryPrice(Double.valueOf(limitOrder.getPrice()));
        request.setTakeProfits(new ArrayList<>());

        Deal deal = new Deal(request);
        deal.setChatId(chatId);
        deal.setStrategyName(strategyName);
        deal.setPositionSize(Double.parseDouble(limitOrder.getQty()));

        LoggerUtils.debug("Создана новая сделка по позиции!" + deal.getSymbol().toString());
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
                LoggerUtils.warn("Ордер не размещён для " + deal.getSymbol() + ": " + fullMessage);
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
        String result = "";
        try {

            double currentPrice = bybitManager.getBybitMarketService().getLastPrice(deal.getSymbol().toString());
            Direction dir = deal.getDirection();


            LoggerUtils.info("!!!!!!!!!!!!!!!!SetSL mrthod. Dral SL = " + deal.getStopLoss() + "CMP = " + currentPrice);


            boolean isInvalidSL = (dir == Direction.SHORT && currentPrice >= deal.getStopLoss()) ||
                    (dir == Direction.LONG  && currentPrice <= deal.getStopLoss());
            if (isInvalidSL) {
                return "⚠️ Уровень SL (" + deal.getStopLoss() + ") уже пройден текущей ценой (" + currentPrice + ").";
            }

            BybitOrderResponse slResponse = bybitManager.getBybitOrderService().setStopLoss(deal);
            String retMsg = slResponse.getRetMsg();

            if (!slResponse.isSuccess()) {
                result = retMsg != null ? retMsg : "No error message from Bybit";
                throw new IllegalStateException("❌ Не удалось установить SL: " + result);
            }

            result = "✅ Стоп-лосс установлен для " + deal.getSymbol() + ": " + deal.getStopLoss();
            LoggerUtils.info(result);
        } catch (Exception e) {
            throw new RuntimeException("❌ Ошибка при установке SL для символа " + deal.getSymbol() + "результат :" + result, e);
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
            LoggerUtils.error("❌ Ошибка при установке TP для символа " + deal.getSymbol(), e);
            throw new RuntimeException("❌ Ошибка при установке TP для символа " + deal.getSymbol(), e);
        }
    }


    public ExitPlan planExit(Deal deal) {
        try {
            LoggerUtils.info("🔍 " + getClass().getSimpleName() + ": Начало сделки " + deal.getId());

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
                LoggerUtils.info("📈 PnL-правила: " + pnlRules);
                LoggerUtils.info("➤ Вызываю ExitPlan.fromPnl() для создания плана по PnL");

                // 🔥 Здесь происходит NoSuchMethodError
                ExitPlan plan = ExitPlan.fromPnl(pnlRules, entryPrice, direction);

                if (plan != null && !plan.getSteps().isEmpty()) {
                    LoggerUtils.info("✅ План по PnL создан");
                    return plan;
                }
            }

            LoggerUtils.warn("⚠️ Не удалось создать план выхода");
            return null;

        } catch (Error err) {
            // ✅ Ловим NoSuchMethodError
            LoggerUtils.error("🔴 FATAL: Ошибка выполнения (возможно, метод не найден)", err);
            return null;
        } catch (Exception e) {
            LoggerUtils.error("Ошибка в planExit()", e);
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
    public void onPriceUpdate(Deal deal, PriceUpdate priceUpdate, UpdateManager updateManager, StopLossManager stopLossManager, BybitManager bybitManager) {
        if (deal == null) {
            LoggerUtils.warn("Straregy - onPriceUpdate - Deal is null");
            return;
        }

        if (!deal.isActive()) {
            LoggerUtils.info("Цена пересекла тейк пока сделка была неактивна - закрыть сделку!!! ");
            //Если цена пересекла тейк пока сделка была неактивна - закрыть сделку
            if (deal.getTakeProfits().get(0) < priceUpdate.getPrice()) {
                bybitManager.getBybitOrderService().closeDeal(deal);
            }
            return;
        }


        double entryPrice = deal.getEntryPrice();
        Direction direction = deal.getDirection();
        double currentPrice = priceUpdate.getPrice();

        if (entryPrice <= 0) {
            LoggerUtils.warn("onPriceUpdate: Некорректная цена входа для сделки " + deal.getId());
            return;
        }

        double pnlPercent;
        if (direction == Direction.LONG) {
            pnlPercent = ((currentPrice - entryPrice) / entryPrice) * 100.0 * deal.getLeverageUsed();
        } else {
            pnlPercent = ((entryPrice - currentPrice) / entryPrice) * 100.0 * deal.getLeverageUsed();
        }
        deal.setPositivePnL(pnlPercent > 0);


        LoggerUtils.debug(deal.getStrategy() + "-" + deal.getSymbol() + ": PnL = " + String.format("%.2f", pnlPercent * deal.getLeverageUsed()) + "%");

        // Получаем правила выхода по PnL из конфига стратегии
        Map<Double, Integer> pnlRules = config.getPnlTpExitRules();
        if (pnlRules.isEmpty()) {
            LoggerUtils.debug("Strategy: Нет правил выхода по PnL в конфиге.");
            return;
        }

        // Проверяем, достигнуты ли уровни PnL
        for (Map.Entry<Double, Integer> ruleEntry : pnlRules.entrySet()) {
            double targetPnlLevel = ruleEntry.getKey() * deal.getLeverageUsed();
            double exitPercentage = ruleEntry.getValue() * deal.getLeverageUsed();

            boolean levelReached = (direction == Direction.LONG && pnlPercent >= targetPnlLevel) ||
                    (direction == Direction.SHORT && pnlPercent >= targetPnlLevel);

            //запуск трейлинга?

            if (levelReached) {
                double lastPositionSize = deal.getPositionSize();
                deal.updateDealFromBybitPosition(updateManager.updateOneDeal(deal.getSymbol().toString()));
                deal.recordExit(deal.getTakeProfits().get(0),lastPositionSize - deal.getPositionSize());

                LoggerUtils.info("Strategy: Достигнут PnL " + String.format("%.2f", targetPnlLevel) +
                        "%. Установлен TP. Планируется выход " + exitPercentage + "% позиции.");


                if (direction == Direction.LONG) {
                    if (deal.getStopLoss() < deal.getEntryPrice()) {
                        stopLossManager.moveStopLoss(deal, deal.getEntryPrice());
                        deal.setPositivePnL(true);
                        LoggerUtils.info("Сделка в БУ!");

                    }
                } else {
                    if (deal.getStopLoss() > deal.getEntryPrice()) {
                        stopLossManager.moveStopLoss(deal, deal.getEntryPrice());
                        deal.setPositivePnL(true);
                        LoggerUtils.info("Сделка в БУ!");
                    }
                }
            }
        }
    }
    @Override
    public void onTakeProfitHit(Deal deal, double executedPrice) {
        LoggerUtils.info(getClass().getSimpleName() + ": Сработал TP на уровне " + executedPrice + ".");
    }
    @Override
    public void onStopLossHit(Deal deal) {
        LoggerUtils.warn(getClass().getSimpleName() + ": Сработал SL.");
    }



}