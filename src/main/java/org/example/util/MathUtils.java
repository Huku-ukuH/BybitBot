package org.example.util;


//Класс с полезными математическими функциями (округление, сравнение с погрешностью и т.д.)
public class MathUtils {
    // Округление до N знаков после запятой
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    // Сравнение с допустимой погрешностью
    public static boolean equalsWithPrecision(double a, double b, double precision) {
        return Math.abs(a - b) < precision;
    }

    // Проверка, находится ли значение в пределах процента от базового
    public static boolean isWithinPercentage(double base, double value, double percentage) {
        double delta = base * (percentage / 100.0);
        return Math.abs(base - value) <= delta;
    }

    // Ограничение значения в диапазоне
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private MathUtils() {
    }
}
