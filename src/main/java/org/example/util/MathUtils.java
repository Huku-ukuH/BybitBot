package org.example.util;


import java.math.BigDecimal;
import java.math.RoundingMode;

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
    public static double formatPrice(double formatExample, double price) {
        if (Double.isNaN(price) || Double.isInfinite(price)) {
            return 0.0;
        }

        // 1. Преобразуем образец в строку → получаем "0.01", а не 0.0100000000000000002
        String exampleStr = String.valueOf(formatExample);

        // 2. Создаём BigDecimal из строки — чтобы избежать ошибок double
        BigDecimal bdExample = new BigDecimal(exampleStr);

        // 3. Определяем количество знаков после запятой
        int scale = bdExample.stripTrailingZeros().scale();

        // 4. Округляем цену до нужного количества знаков
        BigDecimal bdPrice = new BigDecimal(price)
                .setScale(scale, RoundingMode.DOWN);

        // 5. Возвращаем как double
        return bdPrice.doubleValue();
    }
    private MathUtils() {
    }
}
