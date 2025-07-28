package org.example.bybit.auth;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

@Getter
public class BybitAuthConfig {

    private final String apiKey;
    private final String apiSecret;
    private final String baseUrl;

    public BybitAuthConfig() {
        Dotenv dotenv = Dotenv.load();
        this.apiKey = dotenv.get("BYBIT_API_KEY");
        this.apiSecret = dotenv.get("BYBIT_API_SECRET");
        this.baseUrl = dotenv.get("BYBIT_API_BASE_URL");
    }
}
