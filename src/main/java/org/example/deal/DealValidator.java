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
        LoggerUtils.debug("DealValidationResult validate - Начался цикл проверки сделки:");

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
        LoggerUtils.debug("DealValidationResult validate - Прошел цикл проверки сделки:\n" +
                "SL по отношению к ТВХ\nTP по отношению к ТВХ\nTP по отношению к SL\nДальность значений TP и SL\nТекущая цена по монете = " + deal.getEntryPrice());
        return new DealValidationResult(warnings, errors);
    }

    private void checkMissingStopLoss() {
        if (deal.getStopLoss() == null) {
            warnings.add(EmojiUtils.WARN + " Не указан SL\n");
        }
    }
    private void checkMissingTakeProfit() {
        if (deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty()) {
            warnings.add(EmojiUtils.WARN + " Не указан TP\n");
        }
    }
    private void resolveEntryPrice(BybitMarketService bybitMarketService) {
        entryPrice = deal.getEntryPrice();

        if (entryPrice == null || entryPrice <= 0) {
            double currentPrice = bybitMarketService.getLastPrice(deal.getSymbol().toString());
            deal.setEntryPrice(currentPrice);

            try {
                entryPrice = currentPrice;
                warnings.add(EmojiUtils.WARN + " MARKET — current: " + entryPrice + "\n");
                changeEntryPrise = true;
            } catch (Exception e) {
                String errorMsg = "Ошибка получения цены для " + deal.getSymbol() + "\n";
                errors.add(errorMsg);
            }
        }
    }

    private void checkSlVsEntryPrice() {
        Double sl = deal.getStopLoss();
        if (sl == null || entryPrice == null || entryPrice <= 0) return;

        if (deal.getDirection() == Direction.LONG && sl >= entryPrice) {
            errors.add(EmojiUtils.ERROR + " Для LONG SL (" + sl + ") должен быть ниже цены входа (" + entryPrice + ")\n");
        } else if (deal.getDirection() == Direction.SHORT && sl <= entryPrice) {
            errors.add(EmojiUtils.ERROR + " Для SHORT SL (" + sl + ") должен быть выше цены входа (" + entryPrice + ")\n");
        }
    }

    private void checkTpVsEntryPrice() {
        if (deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty() || entryPrice == null || entryPrice <= 0) return;

        for (Double tp : deal.getTakeProfits()) {
            if (deal.getDirection() == Direction.LONG && tp <= entryPrice) {
                errors.add(EmojiUtils.ERROR + " Для LONG TP (" + tp + ") должен быть выше цены входа (" + entryPrice + ")\n");
            } else if (deal.getDirection() == Direction.SHORT && tp >= entryPrice) {
                errors.add(EmojiUtils.ERROR + " Для SHORT TP (" + tp + ") должен быть ниже цены входа (" + entryPrice + ")\n");
            }
        }
    }

    private void checkTpVsSl() {
        if (deal.getStopLoss() == null || deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty()) return;

        for (Double tp : deal.getTakeProfits()) {
            boolean isInvalid = (deal.getDirection() == Direction.LONG && tp <= deal.getStopLoss()) ||
                    (deal.getDirection() == Direction.SHORT && tp >= deal.getStopLoss());
            if (isInvalid) {
                errors.add(EmojiUtils.ERROR + " TP (" + tp + ") должен быть " +
                        (deal.getDirection() == Direction.LONG ? "выше" : "ниже") +
                        " SL (" + deal.getStopLoss() + ") для " + deal.getDirection() + "\n");
            }
        }
    }

    private void checkStopLossDistance() {
        Double sl = deal.getStopLoss();
        if (sl == null || entryPrice == null || entryPrice <= 0) {
            return;
        }
        double slDistancePercent = Math.abs((sl - entryPrice) / entryPrice) * 100;
        slDistancePercent = Math.round(slDistancePercent * 100.0) / 100.0;

        if (slDistancePercent > getMaxDistancePercent()) {
            warnings.add(EmojiUtils.WARN + " SL (" + sl + ") далеко от цены входа (" + entryPrice + ") (" + slDistancePercent + "%)\n");
        }
    }

    private void checkTakeProfitDistance() {
        if (deal.getTakeProfits() == null || deal.getTakeProfits().isEmpty() || entryPrice == null || entryPrice <= 0) {
            return;
        }

        for (Double tp : deal.getTakeProfits()) {
            double tpDistancePercent = Math.abs((tp - entryPrice) / entryPrice) * 100;
            tpDistancePercent = Math.round(tpDistancePercent * 100.0) / 100.0;

            if (tpDistancePercent > getMaxDistancePercent()) {
                warnings.add(EmojiUtils.WARN + " TP (" + tp + ") далеко от цены входа (" + entryPrice + ") (" + tpDistancePercent + "%)\n");
            }
        }
    }
}
