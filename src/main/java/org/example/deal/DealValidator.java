package org.example.deal;

import org.example.bybit.service.BybitMarketService;
import org.example.deal.dto.DealValidationResult;
import org.example.model.Direction;
import org.example.util.EmojiUtils;
import org.example.util.LoggerUtils;
import org.example.util.ValidationUtils;

import java.util.ArrayList;
import java.util.List;

public class DealValidator {

    public double getMaxDistancePercent() {
        return deal.getStrategy().getConfig().getWarningDistancePercent();
    }

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private Deal deal;
    private Double entryPrice;
    boolean changeEntryPrise;

    public DealValidationResult validate(Deal deal, BybitMarketService bybitMarketService) {
        ValidationUtils.checkNotNull(deal, EmojiUtils.WARN + " validate() Deal cannot be null");
        this.deal = deal;

        resolveEntryPrice(bybitMarketService);
        checkMissingStopLoss();
        checkMissingTakeProfit();
        checkSlVsEntryPrice();
        checkTpVsEntryPrice();
        checkTpVsSl();
        checkStopLossDistance();
        checkTakeProfitDistance();

        if (changeEntryPrise) {
            entryPrice = null;
            changeEntryPrise = false;
        }
        return new DealValidationResult(warnings, errors);
    }

    private void checkMissingStopLoss() {
        if (deal.getStopLoss() == null) {
            String errorMsg = EmojiUtils.WARN + " Не указан SL\n";
            LoggerUtils.logWarn(errorMsg);
            warnings.add(errorMsg);
        }
    }
    private void checkMissingTakeProfit() {
        if (deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty()) {
            String errorMsg = EmojiUtils.WARN + " Не указан TP\n";
            LoggerUtils.logWarn(errorMsg);
            warnings.add(errorMsg);
        }
    }
    private void resolveEntryPrice(BybitMarketService bybitMarketService) {
        LoggerUtils.logDebug("resolveEntryPrice() Узнаем цену инструмента");
        entryPrice = deal.getEntryPrice();

        if (entryPrice == null || entryPrice <= 0) {
            double currentPrice = bybitMarketService.getLastPrice(deal.getSymbol().toString());
            LoggerUtils.logDebug("Текущая цена : " + currentPrice);
            deal.setEntryPrice(currentPrice);

            try {
                entryPrice = currentPrice;
                String errorMsg = EmojiUtils.WARN + " MARKET — current: " + entryPrice + "\n";
                warnings.add(errorMsg);
                changeEntryPrise = true;
            } catch (Exception e) {
                String errorMsg = "Ошибка получения цены для " + deal.getSymbol() + "\n";
                errors.add(errorMsg);
            }
        }
    }

    private void checkSlVsEntryPrice() {
        LoggerUtils.logDebug("checkSlVsEntryPrice() Проверяем корректность значений SL по отношению к ТВХ");
        Double sl = deal.getStopLoss();
        if (sl == null || entryPrice == null || entryPrice <= 0) return;

        if (deal.getDirection() == Direction.LONG && sl >= entryPrice) {
            String errorMsg = EmojiUtils.ERROR + " Для LONG SL (" + sl + ") должен быть ниже цены входа (" + entryPrice + ")\n";
            errors.add(errorMsg);
        } else if (deal.getDirection() == Direction.SHORT && sl <= entryPrice) {
            String errorMsg = EmojiUtils.ERROR + " Для SHORT SL (" + sl + ") должен быть выше цены входа (" + entryPrice + ")\n";
            errors.add(errorMsg);
        }
    }

    // Новая проверка TP относительно entryPrice
    private void checkTpVsEntryPrice() {
        LoggerUtils.logDebug("checkTPVsEntryPrice() Проверяем корректность значений TP по отношению к ТВХ");
        if (deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty() || entryPrice == null || entryPrice <= 0) return;

        for (Double tp : deal.getTakeProfits()) {
            if (deal.getDirection() == Direction.LONG && tp <= entryPrice) {
                String errorMsg = EmojiUtils.ERROR + " Для LONG TP (" + tp + ") должен быть выше цены входа (" + entryPrice + ")\n";
                errors.add(errorMsg);
            } else if (deal.getDirection() == Direction.SHORT && tp >= entryPrice) {
                String errorMsg = EmojiUtils.ERROR + " Для SHORT TP (" + tp + ") должен быть ниже цены входа (" + entryPrice + ")\n";
                errors.add(errorMsg);
            }
        }
    }

    private void checkTpVsSl() {
        LoggerUtils.logDebug("checkTpVsSl() Проверяем корректность значений TP по отношению к SL");
        if (deal.getStopLoss() == null || deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty()) return;

        for (Double tp : deal.getTakeProfits()) {
            boolean isInvalid = (deal.getDirection() == Direction.LONG && tp <= deal.getStopLoss()) ||
                    (deal.getDirection() == Direction.SHORT && tp >= deal.getStopLoss());
            if (isInvalid) {
                String errorMsg = EmojiUtils.ERROR + " TP (" + tp + ") должен быть " +
                        (deal.getDirection() == Direction.LONG ? "выше" : "ниже") +
                        " SL (" + deal.getStopLoss() + ") для " + deal.getDirection() + "\n";
                errors.add(errorMsg);
            }
        }
    }

    private void checkStopLossDistance() {
        LoggerUtils.logDebug("checkStopLossDistance() Проверяем дальность значений TP");
        Double sl = deal.getStopLoss();
        if (sl == null || entryPrice == null || entryPrice <= 0) {
            return;
        }
        double slDistancePercent = Math.abs((sl - entryPrice) / entryPrice) * 100;
        slDistancePercent = Math.round(slDistancePercent * 100.0) / 100.0;

        if (slDistancePercent > getMaxDistancePercent()) {
            String errorMsg = EmojiUtils.WARN + " SL (" + sl + ") далеко от цены входа (" + entryPrice + ") (" + slDistancePercent + "%)\n";
            warnings.add(errorMsg);
        }
    }

    private void checkTakeProfitDistance() {
        LoggerUtils.logDebug("checkStopLossDistance() Проверяем дальность значений Sl");
        if (deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty() || entryPrice == null || entryPrice <= 0) {
            return;
        }

        for (Double tp : deal.getTakeProfits()) {
            double tpDistancePercent = Math.abs((tp - entryPrice) / entryPrice) * 100;
            tpDistancePercent = Math.round(tpDistancePercent * 100.0) / 100.0;

            if (tpDistancePercent > getMaxDistancePercent()) {
                String errorMsg = EmojiUtils.WARN + " TP (" + tp + ") далеко от цены входа (" + entryPrice + ") (" + tpDistancePercent + "%)\n";
                warnings.add(errorMsg);
            }
        }
    }
}
