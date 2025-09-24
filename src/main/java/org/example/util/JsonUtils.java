package org.example.util;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;


//Класс парсер

public class JsonUtils {
    private JsonUtils() {
    }
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            String message = String.format("❌ Ошибка сериализации объекта в JSON.\n\nКласс: %s", object.getClass().getName());
            LoggerUtils.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        json = cleanJson(json);
        try {
            return createObjectMapper().readValue(json, clazz);
        } catch (JsonProcessingException e) {
            String message = String.format("❌ Ошибка десериализации JSON в объект.\nJSON: %s\n\nКласс: %s", json, clazz.getName());
            LoggerUtils.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private static String cleanJson(String raw) {
        // Убираем возможные markdown ```json и ```
        String cleaned = raw.replaceAll("(?i)```json", "").replaceAll("```", "").trim();

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return cleaned.substring(start, end + 1).trim();
        } else {
            // Если скобок нет, возвращаем пустую строку или оригинал по желанию
            return "";
        }
    }


    public static ObjectMapper createObjectMapper() { //создает mapper нечувствительный к регистру
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);
        return mapper;
    }
}