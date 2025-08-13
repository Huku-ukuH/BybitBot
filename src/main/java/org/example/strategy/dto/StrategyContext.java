package org.example.strategy.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.bybit.dto.BalanceResponse;
import org.example.bybit.dto.InstrumentInfoResponse;
import org.example.deal.Deal;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class StrategyContext {
    private BalanceResponse accountBalance;
    private InstrumentInfoResponse instrumentInfo;
    private Deal activeDeal;
    private Map<String, Object> metadata;      // Для кастомных данных
}