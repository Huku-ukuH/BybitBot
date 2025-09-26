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
    private double lastTotalUSDTBalance;
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
        BalanceResponse response = httpClient.signedGet(endpoint, queryParams, BalanceResponse.class);

        LoggerUtils.info(response.toString());

        if (response.getResult() == null || response.getResult().getList() == null) {
            throw new RuntimeException("Пустой результат. Проверь accountType или доступ API.");
        }


        LoggerUtils.info("BALANCE = " + response);

        lastTotalUSDTBalance = response.getResult().getList().stream()
                .map(BalanceResponse.BalanceAccount::getTotalEquity) // ✅
                .filter(Objects::nonNull)
                .mapToDouble(Double::parseDouble)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Поле totalEquity не найдено"));

        return lastTotalUSDTBalance;
    }


}
