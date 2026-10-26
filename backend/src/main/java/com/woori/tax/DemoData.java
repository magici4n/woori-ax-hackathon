package com.woori.tax;

import java.math.BigDecimal;
import java.util.List;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import static com.woori.tax.Models.*;

@Component
public class DemoData {
    public static BigDecimal d(String value) { return new BigDecimal(value); }
    // 단일 매수 건. acquisitionKrw에는 당시 환율로 확정된 매수 수수료까지 포함합니다.
    public final List<Lot> lots = List.of(
        new Lot("A-TSLA", "가상 증권사 A", "A-001", "TSLA", "테슬라", 30, d("300"), d("12510000"), d("240"), d("0.0025"), d("31000")),
        new Lot("B-TSLA", "가상 증권사 B", "B-002", "TSLA", "테슬라", 20, d("310"), d("8650000"), d("240"), d("0.0020"), d("17000")),
        new Lot("A-AAPL", "가상 증권사 A", "A-001", "AAPL", "애플", 25, d("220"), d("7750000"), d("200"), d("0.0025"), d("19000")),
        new Lot("B-NVDA", "가상 증권사 B", "B-002", "NVDA", "엔비디아", 40, d("100"), d("5250000"), d("140"), d("0.0020"), d("10000")),
        // 달러 평가손실이어도 과거 원화 취득가액이 작으면 세무상 이익인 사례입니다.
        new Lot("B-AMZN", "가상 증권사 B", "B-002", "AMZN", "아마존", 15, d("210"), d("3500000"), d("200"), d("0.0020"), d("7000"))
    );
    public final List<Account> accounts = List.of(
        new Account("가상 증권사 A", "A-001", d("6000000")),
        new Account("가상 증권사 B", "B-002", d("-1000000"))
    );
    public final Rule rule;
    public DemoData(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("tax-rule.json").getInputStream()) { rule = mapper.readValue(stream, Rule.class); }
    }
    public List<String> symbols() { return lots.stream().map(Lot::symbol).distinct().toList(); }
}
