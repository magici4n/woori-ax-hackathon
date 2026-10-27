package com.woori.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.woori.tax.Models.*;
import static com.woori.tax.ScenarioService.won;

/** Demo portfolio search: tax-loss quantities, fixed lot order, all user limits enforced. */
@Service
public class PortfolioSimulationService {
    private final DemoData data;
    private final ScenarioService calculator;
    public PortfolioSimulationService(DemoData data, ScenarioService calculator) { this.data = data; this.calculator = calculator; }
    private record Option(String symbol, int quantity, BigDecimal gain, BigDecimal fee, BigDecimal cash, BigDecimal soldValue) {}
    private record Candidate(List<Option> options, BigDecimal gain, BigDecimal fee, BigDecimal cash, BigDecimal soldValue, BigDecimal tax) {}

    public PortfolioComparison simulate(SimulationRequest request) {
        Conditions conditions = calculator.validate(request == null ? null : request.conditions());
        List<List<Option>> choices = new ArrayList<>();
        long combinations = 1;
        for (String symbol : data.symbols()) {
            int total = data.lots.stream().filter(l -> l.symbol().equals(symbol)).mapToInt(Lot::quantity).sum();
            int max = calculator.allowedQuantity(total, conditions.symbols().get(symbol));
            List<Option> options = new ArrayList<>();
            for (int q = 0; q <= max; q++) {
                Option o = option(symbol, q);
                if (q == 0 || o.gain().signum() < 0) options.add(o);
            }
            combinations *= options.size();
            if (combinations > 100_000) throw new IllegalArgumentException("시연 계산 범위를 초과했습니다. 매도 한도를 줄여 주세요.");
            choices.add(options);
        }
        List<Candidate> candidates = new ArrayList<>();
        enumerate(choices, 0, new ArrayList<>(), candidates);
        Comparator<Candidate> lessSale = Comparator.comparing(Candidate::soldValue)
            .thenComparing(Candidate::fee).thenComparing(c -> c.options().toString());
        Candidate hold = candidates.stream().filter(c -> c.soldValue().signum() == 0).findFirst().orElseThrow();
        Candidate maximum = candidates.stream().min(Comparator.comparing(Candidate::tax).thenComparing(lessSale)).orElseThrow();
        BigDecimal baseline = calculator.tax(calculator.baselineGain());
        BigDecimal maxReduction = baseline.subtract(maximum.tax());
        BigDecimal target = conditions.targetReductionKrw() == null ? maxReduction.divide(BigDecimal.valueOf(2), 0, RoundingMode.CEILING) : conditions.targetReductionKrw();
        Candidate balanced = candidates.stream().filter(c -> baseline.subtract(c.tax()).compareTo(target) >= 0)
            .min(lessSale).orElse(maximum);
        String partialReason = conditions.targetReductionKrw() == null
            ? "가능한 최대 세금 감소액의 절반 이상을 달성하는 조합 중 매도 평가액이 가장 작은 조합입니다."
            : "희망 세금 감소액을 충족하는 조합 중 매도 평가액을 최소화합니다. 목표가 불가능하면 가능한 최대 감소액을 표시합니다.";
        List<PortfolioScenario> scenarios = List.of(
            describe("hold", "보유 유지", "매도하지 않았을 때의 기준입니다. 현재 보유 종목의 가격 변동에 그대로 참여합니다.", "추가 매도 비용은 없지만 올해 세금 감소도 없습니다.", hold, conditions, null),
            describe("balanced", "일부 절세 · 보유 병행", partialReason, "일부 손실을 확정하고 매도한 수량만큼 이후 상승·하락의 영향을 덜 받습니다.", balanced, conditions, balanced.options().equals(hold.options()) ? "hold" : null),
            describe("tax", "절세 우선", "허용된 손실 실현 조합에서 올해 예상 세금을 최소화하고, 같은 세액이면 매도 평가액이 가장 작은 조합을 선택했습니다.", "세금 감소가 투자 수익을 뜻하지는 않습니다. 손실 확정과 향후 상승 참여 기회의 감소를 함께 비교하세요.", maximum, conditions,
                maximum.options().equals(hold.options()) ? "hold" : maximum.options().equals(balanced.options()) ? "balanced" : null));
        List<String> notices = new ArrayList<>();
        if (maxReduction.signum() == 0) notices.add("현재 조건에서는 추가 절세가 없어 세 시나리오가 같을 수 있습니다.");
        if (conditions.targetReductionKrw() != null && maxReduction.compareTo(conditions.targetReductionKrw()) < 0)
            notices.add("지정한 매도 제한을 지키면 희망 감소액에 도달할 수 없습니다. 가능한 최대 감소액은 " + maxReduction.toPlainString() + "원입니다.");
        notices.add("원화 세무상 손실을 실현하는 조합을 비교합니다. 같은 종목은 시연 계좌 A→B 순서로 매도하며 계좌 선택 최적화는 하지 않습니다.");
        notices.add("기업 자료는 보유·매도의 기회와 위험을 이해하는 참고 정보입니다. 시나리오 수량은 가격 전망 점수가 아닌 손익·세금·매도 한도로 계산합니다.");
        return new PortfolioComparison(baseline, maxReduction, conditions, data.rule, scenarios, notices);
    }
    public PortfolioScenario evaluateProposal(Conditions rawConditions, Map<String,Integer> quantities,
                                              String id, String title, String reason, String tradeoff) {
        Conditions conditions = calculator.validate(rawConditions);
        if (quantities == null || !data.symbols().containsAll(quantities.keySet()))
            throw new IllegalArgumentException("AI가 보유하지 않은 종목을 제안했습니다.");
        List<Option> options = new ArrayList<>();
        BigDecimal gain = BigDecimal.ZERO, fee = BigDecimal.ZERO, cash = BigDecimal.ZERO, soldValue = BigDecimal.ZERO;
        for (String symbol : data.symbols()) {
            int total = data.lots.stream().filter(l -> l.symbol().equals(symbol)).mapToInt(Lot::quantity).sum();
            Integer quantity = quantities.get(symbol);
            if (quantity == null || quantity < 0 || quantity > calculator.allowedQuantity(total, conditions.symbols().get(symbol)))
                throw new IllegalArgumentException("AI 매도 수량이 보유 수량 또는 사용자 제한을 벗어났습니다: " + symbol);
            Option option = option(symbol, quantity);
            options.add(option);
            gain = gain.add(option.gain()); fee = fee.add(option.fee());
            cash = cash.add(option.cash()); soldValue = soldValue.add(option.soldValue());
        }
        Candidate candidate = new Candidate(List.copyOf(options), gain, fee, cash, soldValue,
            calculator.tax(calculator.baselineGain().add(gain)));
        return describe(id, title, reason, tradeoff, candidate, conditions, null);
    }
    public List<Map<String,Object>> planningFacts(Conditions rawConditions) {
        Conditions conditions = calculator.validate(rawConditions);
        List<Map<String,Object>> facts = new ArrayList<>();
        for (String symbol : data.symbols()) {
            List<Lot> lots = data.lots.stream().filter(l -> l.symbol().equals(symbol)).toList();
            int total = lots.stream().mapToInt(Lot::quantity).sum();
            int allowed = calculator.allowedQuantity(total, conditions.symbols().get(symbol));
            facts.add(Map.of("symbol", symbol, "name", lots.get(0).name(), "quantity", total,
                "maxSellQuantity", allowed, "taxGainIfMaxSoldKrw", won(option(symbol, allowed).gain()),
                "marketValueKrw", won(option(symbol, total).soldValue())));
        }
        return facts;
    }
    private Option option(String symbol, int quantity) {
        int left = quantity;
        BigDecimal gain = BigDecimal.ZERO, fee = BigDecimal.ZERO, cash = BigDecimal.ZERO, gross = BigDecimal.ZERO;
        for (Lot lot : data.lots) {
            if (!lot.symbol().equals(symbol)) continue;
            int sold = Math.min(left, lot.quantity()); left -= sold;
            BigDecimal value = lot.priceUsd().multiply(data.rule.fx()).multiply(BigDecimal.valueOf(sold));
            BigDecimal cost = lot.acquisitionKrw().multiply(BigDecimal.valueOf(sold)).divide(BigDecimal.valueOf(lot.quantity()), 12, RoundingMode.HALF_UP);
            BigDecimal commission = value.multiply(lot.sellFeeRate());
            gross = gross.add(value); fee = fee.add(commission); cash = cash.add(value.subtract(commission)); gain = gain.add(value.subtract(commission).subtract(cost));
        }
        return new Option(symbol, quantity, gain, fee, cash, gross);
    }
    private void enumerate(List<List<Option>> choices, int index, List<Option> selected, List<Candidate> result) {
        if (index == choices.size()) {
            BigDecimal gain = BigDecimal.ZERO, fee = BigDecimal.ZERO, cash = BigDecimal.ZERO, value = BigDecimal.ZERO;
            for (Option o : selected) { gain = gain.add(o.gain()); fee = fee.add(o.fee()); cash = cash.add(o.cash()); value = value.add(o.soldValue()); }
            result.add(new Candidate(List.copyOf(selected), gain, fee, cash, value, calculator.tax(calculator.baselineGain().add(gain))));
            return;
        }
        for (Option o : choices.get(index)) { selected.add(o); enumerate(choices, index + 1, selected, result); selected.remove(selected.size() - 1); }
    }
    private PortfolioScenario describe(String id, String title, String reason, String tradeoff, Candidate c, Conditions conditions, String duplicate) {
        BigDecimal totalValue = data.lots.stream().map(l -> l.priceUsd().multiply(data.rule.fx()).multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = totalValue.subtract(c.soldValue());
        BigDecimal reduction = calculator.tax(calculator.baselineGain()).subtract(c.tax());
        List<HoldingPlan> holdings = new ArrayList<>();
        for (Option o : c.options()) {
            List<Lot> lots = data.lots.stream().filter(l -> l.symbol().equals(o.symbol())).toList();
            int total = lots.stream().mapToInt(Lot::quantity).sum();
            Condition condition = conditions.symbols().get(o.symbol());
            String why = o.quantity() > 0 && o.gain().signum() < 0 ? "이 수량의 매도로 원화 세무상 손실을 실현합니다. 기업 전망 악화로 단정한 선택은 아닙니다."
                : o.quantity() > 0 ? "이 수량의 매도로 원화 세무상 이익이 실현될 수 있습니다. 세금 증가와 보유 기회 감소를 함께 비교하세요."
                : condition != null && condition.excluded() ? "사용자가 매도 제외를 요청해 전량 보유합니다."
                : id.equals("hold") ? "매도 없는 기준 시나리오입니다."
                : calculator.allowedQuantity(total, condition) == 0 ? "매도 한도가 0주라 보유합니다."
                : lots.stream().allMatch(l -> calculator.perShareTaxGain(l).signum() >= 0) ? "원화 세무상 이익 종목이라 손실 실현을 통한 절세 후보에 포함하지 않았습니다."
                : "이 조합에서는 해당 종목을 계속 보유합니다.";
            holdings.add(new HoldingPlan(o.symbol(), lots.get(0).name(), total, o.quantity(), total - o.quantity(), won(o.gain()), why));
        }
        List<FutureValue> futures = new ArrayList<>();
        for (int pct : List.of(-10, 0, 10)) futures.add(new FutureValue(pct > 0 ? "+10%" : pct + "%", BigDecimal.valueOf(pct),
            won(c.cash().add(remaining.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(pct, 2)))).subtract(c.tax()))));
        return new PortfolioScenario(id, title, reason, tradeoff, c.tax(), reduction, won(c.gain()), won(c.fee()), won(c.cash()), won(remaining),
            totalValue.signum() == 0 ? BigDecimal.ZERO : remaining.multiply(BigDecimal.valueOf(100)).divide(totalValue, 1, RoundingMode.HALF_UP),
            conditions.targetReductionKrw() == null || reduction.compareTo(conditions.targetReductionKrw()) >= 0, duplicate, holdings, futures);
    }
}
