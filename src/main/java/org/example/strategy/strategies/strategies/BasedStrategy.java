package org.example.strategy.strategies.strategies;
import org.example.strategy.config.StrategyConfig;


/**
 * Базовая стратегия, реализующая стандартную логику управления сделкой. Берет начало из абстрактного класса
 */
public class BasedStrategy extends AbstractStrategy{

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