package org.example.bybit;

import lombok.Getter;
import org.example.bybit.auth.BybitAuthConfig;
import org.example.bybit.client.BybitHttpClient;
import org.example.bybit.service.*;

@Getter
public class BybitManager {
    private final BybitAuthConfig bybitAuthConfig = new BybitAuthConfig();
    private final BybitHttpClient bybitHttpClient = new BybitHttpClient(bybitAuthConfig);
    private final BybitAccountService bybitAccountService = new BybitAccountService(bybitHttpClient);
    private final BybitOrderService bybitOrderService = new BybitOrderService(bybitHttpClient);
    private final BybitMarketService bybitMarketService = new BybitMarketService(bybitHttpClient);
    private final BybitPositionTrackerService bybitPositionTrackerService = new BybitPositionTrackerService(bybitHttpClient);

}
