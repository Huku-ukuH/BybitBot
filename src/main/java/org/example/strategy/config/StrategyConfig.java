// Файл: src/main/java/org/example/strategy/config/StrategyConfig.java
package org.example.strategy.config;

import lombok.Getter;
import lombok.Setter;
import org.example.util.ValuesUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

/**
 * Класс для хранения конфигурационных параметров стратегии.
 */
@Getter
@Setter
public class StrategyConfig {

    private double defaultSlPercent;
    private double maxLossPrecentInPosition;
    private int[] leverageTrails;
    private double warningDistancePercent;
    private Map<Integer, int[]> tpExitRules;
    private Map<Double, Integer> pnlTpExitRules;


    public StrategyConfig() {
        this.defaultSlPercent = ValuesUtil.getDefaultSlPercent();
        this.maxLossPrecentInPosition = ValuesUtil.getDefaultLossPrecent();
        this.leverageTrails = Arrays.copyOf(ValuesUtil.getDefaultLeverageTrails(), ValuesUtil.getDefaultLeverageTrails().length);
        this.warningDistancePercent = ValuesUtil.getWarningDistancePercent();
        this.tpExitRules = ValuesUtil.getDefaultTpExitRules();
        this.pnlTpExitRules = ValuesUtil.getDefaultPnlTpExitRules();
    }

    // Конструктор с пользовательскими параметрами и fallback на дефолтные значения
    public StrategyConfig(Double customSlPercent, Double customMaxLossInPosition, int[] customLeverageTrails,
                          Double customWarningDistancePercent, Map<Integer, int[]> customTpExitRules,
                          Map<Double, Integer> customPnlTpExitRules) {

        this.defaultSlPercent = customSlPercent != null ? customSlPercent : ValuesUtil.getDefaultSlPercent();
        this.maxLossPrecentInPosition = customMaxLossInPosition != null ? customMaxLossInPosition : ValuesUtil.getDefaultLossPrecent();
        this.warningDistancePercent = customWarningDistancePercent != null ? customWarningDistancePercent : ValuesUtil.getWarningDistancePercent();
        this.leverageTrails = customLeverageTrails != null ?
                Arrays.copyOf(customLeverageTrails, customLeverageTrails.length) :
                Arrays.copyOf(ValuesUtil.getDefaultLeverageTrails(), ValuesUtil.getDefaultLeverageTrails().length);
        this.tpExitRules = customTpExitRules != null ?
                new HashMap<>(customTpExitRules) :
                ValuesUtil.getDefaultTpExitRules();
        this.pnlTpExitRules = customPnlTpExitRules != null ?
                new HashMap<>(customPnlTpExitRules) :
                ValuesUtil.getDefaultPnlTpExitRules();
    }

    // Переопределяем для безопасности
    public int[] getLeverageTrails() {
        return Arrays.copyOf(leverageTrails, leverageTrails.length);
    }

    public void setLeverageTrails(int[] leverageTrails) {
        this.leverageTrails = leverageTrails != null ? Arrays.copyOf(leverageTrails, leverageTrails.length) : new int[0];
    }

    // Новые геттер и сеттер для pnlExitRules
    public Map<Double, Integer> getPnlTpExitRules() {
        return pnlTpExitRules != null ? new HashMap<>(pnlTpExitRules) : new HashMap<>();
    }

    public void setPnlTpExitRules(Map<Double, Integer> pnlTpExitRules) {
        this.pnlTpExitRules = pnlTpExitRules != null ? new HashMap<>(pnlTpExitRules) : new HashMap<>();
    }

    @Override
    public String toString() {
        return "StrategyConfig{" +
                "defaultSlPercent=" + defaultSlPercent +
                ", maxLossInPosition=" + maxLossPrecentInPosition +
                ", leverageTrails=" + Arrays.toString(leverageTrails) +
                ", warningDistancePercent=" + warningDistancePercent +
                ", exitRules=" + tpExitRules +
                ", pnlExitRules=" + pnlTpExitRules +
                '}';
    }
}