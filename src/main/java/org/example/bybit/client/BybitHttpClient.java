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

public class BybitHttpClient {

    private final HttpClient client;
    private final BybitAuthConfig authConfig;

    private long timeOffset = 0; // serverTime - localTime
    private final Object timeLock = new Object();

    public BybitHttpClient(BybitAuthConfig authConfig) {
        this.client = HttpClient.newHttpClient();
        this.authConfig = authConfig;

        // Синхронизируем время при старте
        try {
            syncServerTime();
        } catch (IOException e) {
            LoggerUtils.logError("Не удалось синхронизировать время с Bybit при старте", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Запускаем периодическую синхронизацию раз в 2 часа
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncServerTime();
            } catch (IOException e) {
                LoggerUtils.logError("Ошибка при периодической синхронизации времени с Bybit", e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, 120, 120, TimeUnit.MINUTES);
    }

    public <T> T get(String endpoint, Map<String, String> queryParams, Class<T> responseType) {
        try {
            String query = buildQueryString(queryParams);
            String url = authConfig.getBaseUrl() + endpoint + (query.isEmpty() ? "" : "?" + query);

            LoggerUtils.logDebug("GET → endpoint: " + endpoint + ", queryParams: " + queryParams);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-BAPI-API-KEY", authConfig.getApiKey())
                    .GET()
                    .build();

            String body = sendRequest(request);
            LoggerUtils.logDebug("request: " + request);
            LoggerUtils.logDebug("GET ← response: " + body);

            return JsonUtils.fromJson(body, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при GET запросе к Bybit: " + e.getMessage(), e);
        }
    }

    public <T> T post(String endpoint, String jsonBody, Class<T> responseType) {
        try {
            String url = authConfig.getBaseUrl() + endpoint;

            LoggerUtils.logDebug("POST → endpoint: " + endpoint + ", body: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-BAPI-API-KEY", authConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            String body = sendRequest(request);
            LoggerUtils.logDebug("POST ← response: " + body);

            return JsonUtils.fromJson(body, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при POST запросе к Bybit: " + e.getMessage(), e);
        }
    }

    public <T> T signedPost(String endpoint, String jsonBody, Class<T> responseType) {
        try {
            long timestamp = getTimestamp();
            String recvWindow = "10000";

            String signaturePayload = timestamp + authConfig.getApiKey() + recvWindow + jsonBody;
            String signature = BybitRequestUtils.generateSignature(authConfig.getApiSecret(), signaturePayload);

            LoggerUtils.logDebug("SIGNED POST → endpoint: " + endpoint + ", body: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authConfig.getBaseUrl() + endpoint))
                    .header("X-BAPI-API-KEY", authConfig.getApiKey())
                    .header("X-BAPI-SIGN", signature)
                    .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                    .header("X-BAPI-RECV-WINDOW", recvWindow)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            String body = sendRequest(request);
            LoggerUtils.logDebug("SIGNED POST ← response: " + body);

            return JsonUtils.fromJson(body, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при подписанном POST запросе к Bybit: " + e.getMessage(), e);
        }
    }

    public <T> T signedGet(String endpoint, Map<String, String> queryParams, Class<T> responseType) {
        try {
            String recvWindow = "10000";
            String query = buildQueryString(queryParams);
            String queryWithPrefix = query.isEmpty() ? "" : "?" + query;
            long timestamp = getTimestamp();
            String signaturePayload = timestamp + authConfig.getApiKey() + recvWindow + query;
            String signature = BybitRequestUtils.generateSignature(authConfig.getApiSecret(), signaturePayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authConfig.getBaseUrl() + endpoint + queryWithPrefix))
                    .header("X-BAPI-API-KEY", authConfig.getApiKey())
                    .header("X-BAPI-SIGN", signature)
                    .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                    .header("X-BAPI-RECV-WINDOW", recvWindow)
                    .GET()
                    .build();

            String bodyResponse = sendRequest(request);
            LoggerUtils.logDebug("SIGNED GET → endpoint: " + endpoint + ", queryParams: " + queryParams +
                    "\nSIGNED GET ← response:" + bodyResponse);

            return JsonUtils.fromJson(bodyResponse, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при подписанном GET запросе к Bybit: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает синхронизированное с сервером Bybit время в миллисекундах
     */
    private long getTimestamp() {
        synchronized (timeLock) {
            return System.currentTimeMillis() + timeOffset;
        }
    }

    /**
     * Синхронизирует локальное время с серверным временем Bybit
     */
    private void syncServerTime() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authConfig.getBaseUrl() + "/v5/market/time"))
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Не удалось получить время с Bybit: " + response.body());
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

            LoggerUtils.logDebug("Синхронизация времени: server=" + serverTimeMs + " ms, local=" + localTime + " ms, offset=" + timeOffset + " ms");
        } catch (NumberFormatException e) {
            throw new IOException("Ошибка преобразования времени из строки", e);
        } catch (Exception e) {
            throw new IOException("Ошибка парсинга JSON при синхронизации времени", e);
        }
    }

    private String sendRequest(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private String buildQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}