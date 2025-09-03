package org.example.ai;

import chat.giga.client.GigaChatClient;
import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.model.ModelName;
import chat.giga.model.Scope;
import chat.giga.model.completion.ChatMessage;
import chat.giga.model.completion.ChatMessageRole;
import chat.giga.model.completion.CompletionRequest;
import chat.giga.model.completion.CompletionResponse;
import io.github.cdimascio.dotenv.Dotenv;
import org.checkerframework.checker.units.qual.A;
import org.example.deal.dto.DealRequest;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;
import org.example.util.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class AiService {

    public AiService() {}

    /**
     * Отправляет запрос к GigaChat и возвращает ответ.
     */
    private String sendPostRequest(String promptText) {
        try {

            GigaChatClient client = GigaChatClient.builder()
                    .authClient(AuthClient.builder()
                            .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
                                    .scope(Scope.GIGACHAT_API_PERS)
                                    .clientId(Dotenv.load().get("CLIENT_ID"))
                                    .clientSecret(Dotenv.load().get("CLIENT_SECRET"))
                                    .build())
                            .build())
                    .build();
            CompletionResponse response = client.completions(
                    CompletionRequest.builder()
                            .model(ModelName.GIGA_CHAT)
                            .message(ChatMessage.builder()
                                    .content(promptText)
                                    .role(ChatMessageRole.USER)
                                    .build())
                            .build()
            );

            if (response.choices() != null && !response.choices().isEmpty()) {
                String AiResponse = response.choices().get(0).message().content();
                LoggerUtils.logInfo("ответ нйронки " + AiResponse);
                return AiResponse;
            } else {
                LoggerUtils.logInfo("❌❌❌❌❌ AI: Ответ ИИ пустой (choices пуст)❌❌❌❌❌");
                throw new RuntimeException("AI вернул пустой ответ");
            }
        } catch (Exception e) {
            LoggerUtils.logError("🚨 AI: Ошибка при обращении к ИИ", e);
            throw new RuntimeException("Ошибка при обращении к ИИ: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает текст промпта из файла ресурсов.
     */
    private String loadPrompt(String fileName) {
        String resourcePath = "prompts/" + fileName;

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("❌❌❌❌❌ Файл промпта не найден: " + resourcePath + "❌❌❌❌❌");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("❌❌❌❌❌ Ошибка чтения промпта: " + resourcePath  + "❌❌❌❌❌", e);
        }
    }

    /**
     * Парсит сигнал пользователя через ИИ и возвращает структурированный DealRequest.
     */
    public DealRequest parseSignal(String signalText) {
        ValidationUtils.checkNotNull(signalText, "Signal text cannot be null");

        try {
            String promptTemplate = loadPrompt("get_signal_prompt.txt");
            String fullPrompt = promptTemplate + "\n\n### СИГНАЛ\n" + signalText.trim();

            String responseJson = sendPostRequest(fullPrompt);

            return JsonUtils.fromJson(responseJson, DealRequest.class);
        } catch (Exception e) {
            LoggerUtils.logError("❌ AI: Ошибка при парсинге сигнала", e);
            throw new RuntimeException("Ошибка при обработке сигнала ИИ: " + e.getMessage(), e);
        }
    }

    /**
     * Простой чат с ИИ (без промпта).
     */
    public String justChat(String messageText) {
        try {
            String response = sendPostRequest(messageText);
            LoggerUtils.logDebug("🤖 ИИ: " + response);
            return response;
        } catch (Exception e) {
            LoggerUtils.logError("❌ AI: Ошибка в режиме чата", e);
            return "Извините, произошла ошибка при общении с ИИ.";
        }
    }
}