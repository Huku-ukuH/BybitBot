package org.example.strategy.strategies.strategies.superStrategy;

import org.example.ai.AiService;
import org.example.bybit.service.BybitPositionTrackerService;
import org.example.deal.Deal;
import org.example.deal.dto.DealRequest;
import org.example.deal.utils.ActiveDealStore;
import org.example.model.Direction;
import org.example.model.EntryType;
import org.example.model.Symbol;
import org.example.monitor.dto.PositionInfo;
import org.example.util.LoggerUtils;

import java.util.ArrayList;

public class StrategyDealCreator {
    // методы цикла создания Deal будут вызываться в зависимости от требования стратегии.

    public Deal createDealByOpenPosition(PositionInfo positionInfo, long chatId, String strategyName, ActiveDealStore activeDealStore) {
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

        activeDealStore.addDeal(deal);
        deal.setActive(true);

        LoggerUtils.debug("Создана новая сделка по позиции!" + deal.getSymbol().toString());
        return deal;
    }
    public Deal createDealByLimitOrder(BybitPositionTrackerService.OrderInfo limitOrder, long chatId, String strategyName, ActiveDealStore activeDealStore) {
        LoggerUtils.debug("Создание сделки по лимитному ордеру: " + limitOrder.getSymbol());

        DealRequest request = new DealRequest();
        request.setSymbol(limitOrder.getSymbol());
        request.setDirection(Direction.fromString(limitOrder.getSide()));
        request.setEntryType(EntryType.LIMIT);
        request.setEntryPrice(Double.valueOf(limitOrder.getPrice()));
        request.setTakeProfits(new ArrayList<>());

        Deal deal = new Deal(request);
        deal.setChatId(chatId);
        deal.setStrategyName(strategyName);
        deal.setPositionSize(Double.parseDouble(limitOrder.getQty()));

        activeDealStore.addDeal(deal);
        LoggerUtils.debug("Создана новая сделка по лимитному ордеру!" + deal.getSymbol().toString());
        return deal;
    }


    /**
     * метод создания сделки,
     * @param aiService может быть null, если deal создается НЕ из handleGetSignal"
     */

    public Deal createDealBySignal(AiService aiService, String messageText, long chatId, String strategyName, ActiveDealStore activeDealStore) {
        LoggerUtils.debug("Создание сделки по сигналу: " + messageText);
        try {
            DealRequest request = aiService.parseSignal(messageText);
            Deal deal = new Deal(request);
            deal.setChatId(chatId);
            deal.setStrategyName(strategyName);

            activeDealStore.addDeal(deal);
            return deal;
        } catch (Exception e) {
            LoggerUtils.error("❌ Не удалось создать сделку по сигналу: " + messageText, e);
            throw e;
        }
    }

}

//все методы создания сделки будут вызываться стратегией.

//создать?

//установить стоп?

//установить тейк?

//присвоить какое то значение 1 новой Deal?

//присвоить какое то значение 2 новой Deal?
