package org.example.bybit.auth;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

@Getter
public class BybitAuthConfig {

    private final String BYBIT_API_KEY;
    private final String BYBIT_API_SECRET;
    private final String BYBIT_API_BASE_URL;

    public BybitAuthConfig() {
        Dotenv dotenv = Dotenv.load();
        this.BYBIT_API_KEY = dotenv.get("BYBIT_API_KEY");
        this.BYBIT_API_SECRET = dotenv.get("BYBIT_API_SECRET");
        this.BYBIT_API_BASE_URL = dotenv.get("BYBIT_API_BASE_URL");
    }
}
