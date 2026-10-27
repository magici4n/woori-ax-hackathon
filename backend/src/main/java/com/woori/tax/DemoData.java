package com.woori.tax;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import static com.woori.tax.Models.*;

@Component
public class DemoData {
    public static BigDecimal d(String value) { return new BigDecimal(value); }
    private static final Map<String, String> NAMES = Map.of(
        "AAPL", "애플", "MSFT", "마이크로소프트", "NVDA", "엔비디아",
        "INTC", "인텔", "NKE", "나이키", "TSLA", "테슬라");
    private static final BigDecimal DEMO_SELL_FEE_RATE = new BigDecimal("0.001");
    public final List<Lot> lots;
    public final List<Account> accounts;
    public final Rule rule;
    public DemoData(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("tax-rule.json").getInputStream()) { rule = mapper.readValue(stream, Rule.class); }
        // 원본의 현재 보유 스냅샷만 사용합니다. 거래 내역을 재합산하면 수량과 원가가 중복됩니다.
        ObjectMapper decimals = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        JsonNode portfolio;
        try (var stream = new ClassPathResource("demo-portfolio-8081.json").getInputStream()) {
            portfolio = decimals.readTree(stream);
        }
        if (!"SNAPSHOT".equals(portfolio.path("pnlMode").asText())
            || !rule.asOf().equals(portfolio.path("asOf").asText())
            || rule.fx().compareTo(portfolio.path("fx").path("rate").decimalValue()) != 0)
            throw new IOException("시연 포트폴리오의 기준일·환율·손익 모드가 세금 규칙과 다릅니다.");
        Map<String, String> brokers = new LinkedHashMap<>();
        List<Account> loadedAccounts = new ArrayList<>();
        for (JsonNode row : portfolio.path("accounts")) {
            String id = row.path("id").asText();
            String broker = row.path("broker").asText();
            if (id.isBlank() || broker.isBlank() || brokers.putIfAbsent(id, broker) != null)
                throw new IOException("시연 계좌 ID 또는 증권사 정보가 올바르지 않습니다.");
            loadedAccounts.add(new Account(broker, id, row.path("snapshotPnlKrw").decimalValue()));
        }
        List<Lot> loadedLots = new ArrayList<>();
        for (JsonNode row : portfolio.path("holdings")) {
            String account = row.path("accountId").asText();
            String symbol = row.path("ticker").asText();
            int quantity = row.path("quantity").asInt();
            if (!brokers.containsKey(account) || !NAMES.containsKey(symbol) || quantity <= 0)
                throw new IOException("시연 보유 종목 또는 계좌 정보가 올바르지 않습니다.");
            BigDecimal principalUsd = row.path("principalUsd").decimalValue();
            loadedLots.add(new Lot(account + "-" + symbol, brokers.get(account), account, symbol,
                NAMES.get(symbol), quantity,
                principalUsd.divide(BigDecimal.valueOf(quantity), MathContext.DECIMAL128),
                row.path("costKrw").decimalValue(), row.path("priceUsd").decimalValue(),
                DEMO_SELL_FEE_RATE, BigDecimal.ZERO));
        }
        if (loadedAccounts.isEmpty() || loadedLots.isEmpty()) throw new IOException("시연 포트폴리오가 비어 있습니다.");
        accounts = List.copyOf(loadedAccounts);
        lots = List.copyOf(loadedLots);
    }
    public List<String> symbols() { return lots.stream().map(Lot::symbol).distinct().toList(); }
}
