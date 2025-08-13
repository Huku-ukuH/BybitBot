package org.example.bybit.service;

import lombok.Getter;
import org.example.bybit.dto.BalanceResponse;
import org.example.bybit.client.BybitHttpClient;
import org.example.util.LoggerUtils;

import java.util.Map;
import java.util.Objects;

public class BybitAccountService {
    private final BybitHttpClient httpClient;
    @Getter
    private double lastUsdtBalanceInfo;
    public BybitAccountService(BybitHttpClient httpClient) {
        this.httpClient = httpClient;
    }


    /**
     * Получает баланс USDT на деривативном аккаунте (accountType = CONTRACT).
     *
     * @return объект ответа с балансом
     */
    public double getUsdtBalance() {
        String endpoint = "/v5/account/wallet-balance";
        Map<String, String> queryParams = Map.of("accountType", "unified");
        // найти почему BalanceResponse(retCode=0, retMsg=invalid request
        BalanceResponse response = httpClient.signedGet(endpoint, queryParams, BalanceResponse.class);

        LoggerUtils.logInfo(response.toString());

        if (response.getResult() == null || response.getResult().getList() == null) {
            throw new RuntimeException("Пустой результат. Проверь accountType или доступ API.");
        }

        // Получаем totalAvailableBalance из первого аккаунта (если нужно — добавь фильтрацию по accountType)
        lastUsdtBalanceInfo = response.getResult().getList().stream()
                .map(BalanceResponse.BalanceAccount::getTotalAvailableBalance)
                .filter(Objects::nonNull)
                .mapToDouble(Double::parseDouble)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Поле totalAvailableBalance не найдено"));

        return lastUsdtBalanceInfo;
    }


}
