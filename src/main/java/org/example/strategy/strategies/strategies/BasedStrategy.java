package org.example.strategy.strategies.strategies;
import org.example.deal.Deal;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.strategies.strategies.superStrategy.AbstractStrategy;


/**
 * Базовая стратегия, реализующая стандартную логику управления сделкой. Берет начало из абстрактного класса
 */
public class BasedStrategy extends AbstractStrategy {

    protected StrategyConfig createConfig() {
        return new StrategyConfig(
                null,
                null,
                new int[]{10, 20},
                null,
                null,
                null
        );
    }

}