// Файл: src/main/java/org/example/bybit/client/BybitHttpClient.java
package org.example.bybit.client;

import org.example.bybit.auth.BybitAuthConfig;
import org.example.util.BybitRequestUtils;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HTTP клиент для взаимодействия с API Bybit.
 * Включает синхронизацию времени, управление подписями для приватных запросов
 * и ограничение частоты запросов.
 */
public class BybitHttpClient {

    private final HttpClient client;
    private final BybitAuthConfig authConfig;

    private long timeOffset = 0; // serverTime - localTime
    private final Object timeLock = new Object();

    // --- Rate Limiter ---
    // Пример: максимум 100 запросов в минуту, пауза 10 секунд при превышении
    // TODO: Уточните лимиты для вашего ключа/API Bybit
    private final RateLimiter rateLimiter = new RateLimiter(100, TimeUnit.MINUTES.toMillis(1), TimeUnit.SECONDS.toMillis(10));
    // --------------------

    /**
     * Конструктор.
     *
     * @param authConfig Конфигурация аутентификации Bybit.
     */
    public BybitHttpClient(BybitAuthConfig authConfig) {
        this.client = HttpClient.newHttpClient();
        this.authConfig = authConfig;

        // Синхронизируем время при старте
        try {
            syncServerTime();
        } catch (IOException e) {
            LoggerUtils.error("Не удалось синхронизировать время с Bybit при старте", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Восстанавливаем статус прерывания
            throw new RuntimeException("Поток прерван во время синхронизации времени", e);
        }

        // Запускаем периодическую синхронизацию раз в 2 часа
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "BybitTimeSyncThread");
            t.setDaemon(true); // Позволяем JVM завершиться, даже если поток активен
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncServerTime();
            } catch (IOException e) {
                LoggerUtils.error("Ошибка при периодической синхронизации времени с Bybit", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LoggerUtils.warn("Поток синхронизации времени прерван.");
                throw new RuntimeException(e);
            }
        }, 120, 120, TimeUnit.MINUTES);
        // Не забудьте закрыть scheduler при завершении приложения в TradingBotApplication
        // scheduler.shutdown();
    }

    /**
     * Выполняет GET-запрос к публичному эндпоинту API Bybit.
     *
     * @param endpoint     Эндпоинт API (например, "/v5/market/tickers").
     * @param queryParams  Параметры запроса.
     * @param responseType Класс ожидаемого типа ответа.
     * @param <T>          Тип ответа.
     * @return Десериализованный ответ от API.
     * @throws RuntimeException Если запрос завершился ошибкой.
     */
    public <T> T get(String endpoint, Map<String, String> queryParams, Class<T> responseType) {
        try {
            String query = buildQueryString(queryParams);
            String url = authConfig.getBYBIT_API_BASE_URL() + endpoint + (query.isEmpty() ? "" : "?" + query);

            LoggerUtils.debug("GET → endpoint: " + endpoint + ", queryParams: " + queryParams);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            // Не добавляем API-ключ для публичных эндпоинтов
            boolean isPublicMarketEndpoint = endpoint != null && endpoint.startsWith("/v5/market/");
            if (!isPublicMarketEndpoint) {
                addApiKeyHeader(requestBuilder);
            } else {
                LoggerUtils.debug("Запрос к публичному эндпоинту " + endpoint + ", заголовок X-BAPI-API-KEY не добавляется.");
            }

            HttpRequest request = requestBuilder.build();

            String body = sendRequestWithRateLimit(request);
            LoggerUtils.debug("GET ← response: " + body);

            return JsonUtils.fromJson(body, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при GET запросе к Bybit: " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет POST-запрос к приватному эндпоинту API Bybit.
     *
     * @param endpoint     Эндпоинт API (например, "/v5/order/create").
     * @param jsonBody     Тело запроса в формате JSON.
     * @param responseType Класс ожидаемого типа ответа.
     * @param <T>          Тип ответа.
     * @return Десериализованный ответ от API.
     * @throws RuntimeException Если запрос завершился ошибкой.
     */
    public <T> T post(String endpoint, String jsonBody, Class<T> responseType) {
        try {
            String url = authConfig.getBYBIT_API_BASE_URL() + endpoint;

            LoggerUtils.debug("POST → endpoint: " + endpoint + ", body: " + jsonBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            // Добавляем API-ключ для POST (обычно приватные)
            addApiKeyHeader(requestBuilder);

            HttpRequest request = requestBuilder.build();

            String body = sendRequestWithRateLimit(request);
            LoggerUtils.debug("POST ← response: " + body);

            return JsonUtils.fromJson(body, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при POST запросе к Bybit: " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет подписанный POST-запрос к приватному эндпоинту API Bybit.
     *
     * @param endpoint     Эндпоинт API (например, "/v5/order/create").
     * @param jsonBody     Тело запроса в формате JSON.
     * @param responseType Класс ожидаемого типа ответа.
     * @param <T>          Тип ответа.
     * @return Десериализованный ответ от API.
     * @throws RuntimeException Если запрос завершился ошибкой.
     */
    public <T> T signedPost(String endpoint, String jsonBody, Class<T> responseType) {
        try {
            long timestamp = getTimestamp();
            String recvWindow = "10000"; // Можно сделать настраиваемым через конфиг

            String signaturePayload = timestamp + authConfig.getBYBIT_API_KEY() + recvWindow + jsonBody;
            String signature = BybitRequestUtils.generateSignature(authConfig.getBYBIT_API_SECRET(), signaturePayload);

            LoggerUtils.debug("SIGNED POST → endpoint: " + endpoint + ", body: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authConfig.getBYBIT_API_BASE_URL() + endpoint))
                    .header("X-BAPI-API-KEY", authConfig.getBYBIT_API_KEY())
                    .header("X-BAPI-SIGN", signature)
                    .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                    .header("X-BAPI-RECV-WINDOW", recvWindow)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            String body = sendRequestWithRateLimit(request);
            LoggerUtils.debug("SIGNED POST ← response: " + body);

            return JsonUtils.fromJson(body, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при подписанном POST запросе к Bybit: " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет подписанный GET-запрос к приватному эндпоинту API Bybit.
     *
     * @param endpoint     Эндпоинт API (например, "/v5/order/realtime").
     * @param queryParams  Параметры запроса.
     * @param responseType Класс ожидаемого типа ответа.
     * @param <T>          Тип ответа.
     * @return Десериализованный ответ от API.
     * @throws RuntimeException Если запрос завершился ошибкой.
     */
    public <T> T signedGet(String endpoint, Map<String, String> queryParams, Class<T> responseType) {
        try {
            String recvWindow = "10000"; // Можно сделать настраиваемым через конфиг
            String query = buildQueryString(queryParams);
            String queryWithPrefix = query.isEmpty() ? "" : "?" + query;
            long timestamp = getTimestamp();
            String signaturePayload = timestamp + authConfig.getBYBIT_API_KEY() + recvWindow + query;
            String signature = BybitRequestUtils.generateSignature(authConfig.getBYBIT_API_SECRET(), signaturePayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authConfig.getBYBIT_API_BASE_URL() + endpoint + queryWithPrefix))
                    .header("X-BAPI-API-KEY", authConfig.getBYBIT_API_KEY())
                    .header("X-BAPI-SIGN", signature)
                    .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                    .header("X-BAPI-RECV-WINDOW", recvWindow)
                    .GET()
                    .build();

            String bodyResponse = sendRequestWithRateLimit(request);
            LoggerUtils.debug("SIGNED GET → endpoint: " + endpoint + ", queryParams: " + queryParams +
                    "\nSIGNED GET ← response:" + bodyResponse);

            return JsonUtils.fromJson(bodyResponse, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при подписанном GET запросе к Bybit: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает синхронизированное с сервером Bybit время в миллисекундах.
     *
     * @return Синхронизированное время.
     */
    private long getTimestamp() {
        synchronized (timeLock) {
            return System.currentTimeMillis() + timeOffset;
        }
    }

    /**
     * Синхронизирует локальное время с серверным временем Bybit.
     *
     * @throws IOException          Если возникла ошибка ввода-вывода при запросе.
     * @throws InterruptedException Если поток был прерван.
     */
    private void syncServerTime() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authConfig.getBYBIT_API_BASE_URL() + "/v5/market/time"))
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Не удалось получить время с Bybit: HTTP " + response.statusCode() + " - " + response.body());
        }

        try {
            ObjectMapper mapper = JsonUtils.createObjectMapper();
            Map<String, Object> root = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});

            // Проверка retCode
            Object retCodeObj = root.get("retCode");
            if (retCodeObj == null || !retCodeObj.toString().equals("0")) {
                throw new IOException("Ошибка от Bybit при запросе времени: " + response.body());
            }

            Object resultObj = root.get("result");
            if (!(resultObj instanceof Map)) {
                throw new IOException("Неверный формат поля 'result' в ответе");
            }

            Map<String, String> result = (Map<String, String>) resultObj;
            String timeNanoStr = result.get("timeNano");

            long serverTimeMs;
            if (timeNanoStr != null && !timeNanoStr.isEmpty()) {
                serverTimeMs = Long.parseLong(timeNanoStr) / 1_000_000; // нано -> милли
            } else {
                String timeSecondStr = result.get("timeSecond");
                if (timeSecondStr == null || timeSecondStr.isEmpty()) {
                    throw new IOException("Не удалось извлечь время: ни timeNano, ни timeSecond не найдены");
                }
                serverTimeMs = Long.parseLong(timeSecondStr) * 1000; // секунды -> милли
            }

            long localTime = System.currentTimeMillis();
            synchronized (timeLock) {
                this.timeOffset = serverTimeMs - localTime;
            }

            LoggerUtils.debug("Синхронизация времени: server=" + serverTimeMs + " ms, local=" + localTime + " ms, offset=" + timeOffset + " ms");
        } catch (NumberFormatException e) {
            throw new IOException("Ошибка преобразования времени из строки", e);
        } catch (Exception e) {
            throw new IOException("Ошибка парсинга JSON при синхронизации времени", e);
        }
    }

    /**
     * Отправляет HTTP-запрос и возвращает тело ответа.
     * Выполняет проверку кода состояния HTTP.
     *
     * @param request HTTP-запрос.
     * @return Тело ответа.
     * @throws IOException          Если возникла ошибка ввода-вывода.
     * @throws InterruptedException Если поток был прерван.
     */
    private String sendRequest(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            // Логируем полный запрос для отладки
            LoggerUtils.warn("HTTP-запрос завершился ошибкой: " + request.method() + " " + request.uri() +
                    " -> HTTP " + response.statusCode() + " - " + response.body());
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * Отправляет HTTP-запрос с учетом ограничения частоты.
     * Перед отправкой вызывает {@link RateLimiter#acquire()}.
     *
     * @param request HTTP-запрос.
     * @return Тело ответа.
     * @throws IOException          Если возникла ошибка ввода-вывода.
     * @throws InterruptedException Если поток был прерван (включая паузу в RateLimiter).
     */
    private String sendRequestWithRateLimit(HttpRequest request) throws IOException, InterruptedException {
        // 1. Проверяем лимит перед отправкой
        rateLimiter.acquire();
        LoggerUtils.debug("RateLimiter: Запрос разрешен. Отправка " + request.method() + " " + request.uri());

        // 2. Отправляем запрос
        return sendRequest(request);
    }

    /**
     * Строит строку запроса из карты параметров.
     *
     * @param params Карта параметров.
     * @return Строка запроса (без '?' в начале).
     */
    private String buildQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            // Убедимся, что ключ и значение не null перед кодированием
            String key = entry.getKey() != null ? entry.getKey() : "";
            String value = entry.getValue() != null ? entry.getValue() : "";
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * Добавляет заголовок X-BAPI-API-KEY к запросу, если ключ доступен.
     *
     * @param requestBuilder Строитель HTTP-запроса.
     */
    private void addApiKeyHeader(HttpRequest.Builder requestBuilder) {
        String apiKey = authConfig.getBYBIT_API_KEY();
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("X-BAPI-API-KEY", apiKey);
        } else {
            LoggerUtils.warn("API ключ отсутствует или пуст. Заголовок X-BAPI-API-KEY не будет добавлен.");
        }
    }
}