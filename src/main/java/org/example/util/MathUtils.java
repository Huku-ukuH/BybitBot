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


    private MathUtils() {
    }
}
