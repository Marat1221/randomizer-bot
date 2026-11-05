package com.example.demo.entity;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterExample {

    private final OpenAIClient client;

    // Ключ и модель лучше держать в application.properties,
    // чтобы не коммитить их в гит
    public OpenRouterExample(
            @Value("${openrouter.api.key}") String apiKey,
            @Value("${openrouter.model:google/gemini-2.0-flash-exp:free}") String model) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl("https://openrouter.ai/api/v1")
                .build();
        this.model = model;
    }

    private final String model;

    /** Отправить одно сообщение и вернуть текст ответа. */
    public String ask(String userMessage) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .addUserMessage(userMessage)
                .build();

        ChatCompletion chatCompletion = client.chat().completions().create(params);
        return chatCompletion.choices().get(0).message().content().orElse("");
    }
}