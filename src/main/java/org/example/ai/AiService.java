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
                                    .clientId(Dotenv.load().get("CLIENT_ID"))                // ОБЯЗАТЕЛЬНО
                                    .clientSecret(Dotenv.load().get("CLIENT_SECRET"))
                                    .build())
                            .build())
                    .build();
            CompletionResponse response = client.completions(
                    CompletionRequest.builder()
                            .model(ModelName.GIGA_CHAT) // или другая модель
                            .message(ChatMessage.builder()
                                    .content(promptText)  // <<== Здесь подставляем promptText
                                    .role(ChatMessageRole.USER)
                                    .build())
                            .build()
            );


            if (response.choices() != null && !response.choices().isEmpty()) {
                LoggerUtils.logInfo(response.choices().get(0).message().content());
                return response.choices().get(0).message().content();
            } else {
                throw new RuntimeException("AI вернул пустой ответ");
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обращении к ИИ: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает текст промпта из файла ресурсов.
     */
    private String loadPrompt(String fileName) {
        fileName = "prompts/" + fileName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IllegalArgumentException("Файл промпта не найден: " + fileName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения промпта: " + fileName, e);
        }
    }

    public DealRequest parseSignal(String signalText) {
        ValidationUtils.checkNotNull(signalText, "Signal text cannot be null");
        String prompt = loadPrompt("get_signal_prompt.txt") + "\n" + signalText;
        String responseJson = sendPostRequest(prompt);
        return JsonUtils.fromJson(responseJson, DealRequest.class);
    }


    public String justChat(String messageText) {
        return sendPostRequest(messageText);
    }
}


