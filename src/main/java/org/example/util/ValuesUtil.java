// org/example/util/ValuesUtil.java
package org.example.util;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ValuesUtil {
    @Getter
    private static final double defaultSlPercent = 0.10;
    @Getter
    private static final double defaultMaxLossInPosition = 7; // Предполагая, что это теперь double
    @Getter
    private static final int[] defaultLeverageTrails = {20, 15, 10, 7};
    @Getter
    private static final double warningDistancePercent = 20.0;

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
    /**
     * Возвращает копию стандартных правил выхода.
     */
    public static Map<Integer, int[]> getDefaultTpExitRules() {
        // Создаем глубокую копию, так как массивы int[] изменяемы
        Map<Integer, int[]> copy = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : DEFAULT_TP_EXIT_RULES.entrySet()) {
            copy.put(entry.getKey(), java.util.Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }


    private static final Map<Double, Integer> DEFAULT_PNL_TP_EXIT_RULES = new HashMap<>();

    static {
        // Пример дефолтных правил: при достижении 8%, 15%, 23% PnL
        // закрывать 50%, 25%, 12% от позиции соответственно.
        DEFAULT_PNL_TP_EXIT_RULES.put(8.0, 50);
        DEFAULT_PNL_TP_EXIT_RULES.put(15.0, 25);
        DEFAULT_PNL_TP_EXIT_RULES.put(23.0, 12);

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