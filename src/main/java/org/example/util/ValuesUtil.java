// org/example/util/ValuesUtil.java
package org.example.util;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ValuesUtil {
    @Getter
    @Setter
    private static volatile double defaultSlPercent = 0.10;
    @Getter
    @Setter
    private static volatile double maxLossInPosition = 7; // Предполагая, что это теперь double
    @Getter
    @Setter
    private static volatile int[] leverageTrails = {20, 15, 10, 7};
    @Getter
    @Setter
    private static volatile double warningDistancePercent = 20.0;


    private static final Map<Integer, int[]> DEFAULT_EXIT_RULES = new HashMap<>();

    static {
        DEFAULT_EXIT_RULES.put(1, new int[]{100});
        DEFAULT_EXIT_RULES.put(2, new int[]{50, 50});
        DEFAULT_EXIT_RULES.put(3, new int[]{50, 25, 25});
        DEFAULT_EXIT_RULES.put(4, new int[]{40, 25, 20, 15});
        DEFAULT_EXIT_RULES.put(5, new int[]{30, 20, 20, 15, 15});
        DEFAULT_EXIT_RULES.put(6, new int[]{30, 20, 15, 13, 12, 10});
        DEFAULT_EXIT_RULES.put(7, new int[]{25, 18, 15, 12, 12, 10, 8});
        DEFAULT_EXIT_RULES.put(8, new int[]{22, 16, 14, 10, 9, 8, 7, 4});
        DEFAULT_EXIT_RULES.put(9, new int[]{20, 15, 13, 10, 9, 8, 7, 6, 2});
    }

    /**
     * Возвращает копию стандартных правил выхода.
     * Это позволяет избежать изменения оригинальных правил внешним кодом.
     * @return Карта стандартных правил выхода.
     */
    public static Map<Integer, int[]> getDefaultExitRules() {
        // Создаем глубокую копию, так как массивы int[] изменяемы
        Map<Integer, int[]> copy = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : DEFAULT_EXIT_RULES.entrySet()) {
            copy.put(entry.getKey(), java.util.Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }

    private ValuesUtil() {
    }
}