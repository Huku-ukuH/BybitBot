// org/example/util/ValuesUtil.java
package org.example.util;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class  ValuesUtil {
    @Getter
    //значение-процент депозита который можно потерять за сделку
    private static final double defaultLossPrecent = 1;
    @Getter
    //процент для предупреждения о слишком дальнем тейке или стопе
    private static final double warningDistancePercent = 30.0;
    @Getter
    //процент отступа для стоп лосса по умолчанию
    private static final double defaultSlPercent = 0.20;
    @Getter
    //порядок проверки плечей для позиции
    private static final int[] defaultLeverageTrails = {20, 15, 10, 7};
    //правила выхода - количество тейков - %позиции на тейк
    private static final Map<Integer, int[]> DEFAULT_TP_EXIT_RULES = new HashMap<>();

    static {
        DEFAULT_TP_EXIT_RULES.put(1, new int[]{100});
        DEFAULT_TP_EXIT_RULES.put(2, new int[]{50, 50});
        DEFAULT_TP_EXIT_RULES.put(3, new int[]{50, 25, 25});
        DEFAULT_TP_EXIT_RULES.put(4, new int[]{40, 25, 20, 15});
        DEFAULT_TP_EXIT_RULES.put(5, new int[]{30, 20, 20, 15, 15});
        DEFAULT_TP_EXIT_RULES.put(6, new int[]{30, 20, 15, 13, 12, 10});
        DEFAULT_TP_EXIT_RULES.put(7, new int[]{25, 18, 15, 12, 12, 10, 8});
        DEFAULT_TP_EXIT_RULES.put(8, new int[]{22, 16, 14, 10, 9, 8, 7, 4});
        DEFAULT_TP_EXIT_RULES.put(9, new int[]{20, 15, 13, 10, 9, 8, 7, 6, 2});
    }
    // правила выхода по значению pnl
    private static final Map<Double, Integer> DEFAULT_PNL_TP_EXIT_RULES = new HashMap<>();
    static {
        Double firstPnlValue = 8.0;
        Double secondPnlValue = 15.0;
        Double thirdPnlValue = 23.0;
        Integer firstFixPositionPresentage = 50;
        Integer secondFixPositionPresentage = 25;
        Integer thirdFixPositionPresentage = 12;
        DEFAULT_PNL_TP_EXIT_RULES.put(firstPnlValue, firstFixPositionPresentage);
        DEFAULT_PNL_TP_EXIT_RULES.put(secondPnlValue, secondFixPositionPresentage);
        DEFAULT_PNL_TP_EXIT_RULES.put(thirdPnlValue, thirdFixPositionPresentage);

    }

    public static Map<Integer, int[]> getDefaultTpExitRules() {
        // Создаем глубокую копию, так как массивы int[] изменяемы
        Map<Integer, int[]> copy = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : DEFAULT_TP_EXIT_RULES.entrySet()) {
            copy.put(entry.getKey(), java.util.Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }


    /**
     * Возвращает копию стандартных правил выхода по PnL.
     */
    public static Map<Double, Integer> getDefaultPnlTpExitRules() {
        // HashMap конструктор создает изменяемую копию
        Map<Double, Integer> copy = new HashMap<>(DEFAULT_PNL_TP_EXIT_RULES);
        // Возвращаем неизменяемую обертку
        return Collections.unmodifiableMap(copy);
    }


    private ValuesUtil() {
    }
}