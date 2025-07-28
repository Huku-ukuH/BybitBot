package org.example.bybit.client;

import org.example.bybit.auth.BybitAuthConfig;
import org.example.util.BybitRequestUtils;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BybitHttpClient {

    private final HttpClient client;
    private final BybitAuthConfig authConfig;

    public BybitHttpClient(BybitAuthConfig authConfig) {
        this.client = HttpClient.newHttpClient();
        this.authConfig = authConfig;
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
            long timestamp = BybitRequestUtils.getCurrentTimestamp();
            String recvWindow = "5000";

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
            String recvWindow = "8000";
            String query = buildQueryString(queryParams);
            String queryWithPrefix = query.isEmpty() ? "" : "?" + query;
            long timestamp = BybitRequestUtils.getCurrentTimestamp();
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
            if (!sb.isEmpty()) sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
