
package org.example.strategy.config;

import lombok.Getter;
import lombok.Setter;
import org.example.util.ValuesUtil;

import java.util.Arrays;
import java.util.Map;

/**
 * Класс для хранения конфигурационных параметров стратегии.
 * Может использовать значения по умолчанию из ValuesUtil или задавать собственные.
 */
@Getter
@Setter
public class StrategyConfig {
    private double defaultSlPercent;
    private double maxLossInPosition;
    private int[] leverageTrails;
    private double warningDistancePercent;
    private Map<Integer, int[]> exitRules;

    /**
     * Конструктор по умолчанию.
     * Инициализирует параметры значениями из ValuesUtil.
     */
    public StrategyConfig() {
        this.defaultSlPercent = ValuesUtil.getDefaultSlPercent();
        this.maxLossInPosition = ValuesUtil.getMaxLossInPosition();
        this.leverageTrails = Arrays.copyOf(ValuesUtil.getLeverageTrails(), ValuesUtil.getLeverageTrails().length);
        this.warningDistancePercent = ValuesUtil.getWarningDistancePercent();
        this.exitRules = ValuesUtil.getDefaultExitRules();
    }

    //Конструктор с пользовательскими параметрами.
    public StrategyConfig(double defaultSlPercent, int maxLossInPosition, int[] leverageTrails, double warningDistancePercent, Map<Integer, int[]> customExitRules) {
        this.defaultSlPercent = defaultSlPercent;
        this.maxLossInPosition = maxLossInPosition;
        this.leverageTrails = leverageTrails != null ? Arrays.copyOf(leverageTrails, leverageTrails.length) : new int[0];
        this.warningDistancePercent = warningDistancePercent;
        this.exitRules = customExitRules;
    }



    public int[] getLeverageTrails() {
        // Возвращаем копию массива, чтобы предотвратить модификацию внутреннего состояния
        return Arrays.copyOf(leverageTrails, leverageTrails.length);
    }


    public void setLeverageTrails(int[] leverageTrails) {
        // Создаем копию массива, чтобы избежать внешних изменений
        this.leverageTrails = leverageTrails != null ? Arrays.copyOf(leverageTrails, leverageTrails.length) : new int[0];
    }


    @Override
    public String toString() {
        return "StrategyConfig{" +
                "defaultSlPercent=" + defaultSlPercent +
                ", maxLossInPosition=" + maxLossInPosition +
                ", leverageTrails=" + Arrays.toString(leverageTrails) +
                ", warningDistancePercent=" + warningDistancePercent +
                '}';
    }
}