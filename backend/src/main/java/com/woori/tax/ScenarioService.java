package com.woori.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.woori.tax.Models.*;
import static com.woori.tax.DemoData.d;

@Service
public class ScenarioService {
    private final DemoData data;
    public ScenarioService(DemoData data) { this.data = data; }
    public static BigDecimal won(BigDecimal n) { return n.setScale(0, RoundingMode.HALF_UP); }
    public static BigDecimal sumRealized(List<Account> accounts) { return accounts.stream().map(Account::realizedKrw).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal baselineGain() { return sumRealized(data.accounts); }
    public BigDecimal tax(BigDecimal gain) {
        return won(gain.subtract(data.rule.deductionKrw()).max(BigDecimal.ZERO)
            .multiply(data.rule.nationalRate().add(data.rule.localRate())));
    }
    public Conditions validate(Conditions input) {
        if (input == null) return Conditions.empty();
        Map<String, Condition> result = new TreeMap<>();
        if (input.symbols() != null) {
            if (input.symbols().size() > 10) throw new IllegalArgumentException("조건 개수가 너무 많습니다.");
            input.symbols().forEach((symbol, c) -> {
                if (!data.symbols().contains(symbol) || c == null) throw new IllegalArgumentException("보유 종목의 조건만 설정할 수 있습니다: " + symbol);
                int quantity = data.lots.stream().filter(x -> x.symbol().equals(symbol)).mapToInt(Lot::quantity).sum();
                if (c.maxShares() != null && (c.maxShares() < 0 || c.maxShares() > quantity)) throw new IllegalArgumentException(symbol + " 최대 수량은 0~" + quantity + "주입니다.");
                if (c.maxPercent() != null && (c.maxPercent().signum() < 0 || c.maxPercent().compareTo(d("100")) > 0)) throw new IllegalArgumentException("비율은 0~100%입니다.");
                result.put(symbol, c);
            });
        }
        if (input.targetReductionKrw() != null && (input.targetReductionKrw().signum() < 0 || input.targetReductionKrw().compareTo(d("1000000000")) > 0)) throw new IllegalArgumentException("목표 감소액은 0~10억원입니다.");
        if (input.cashNeedKrw() != null && (input.cashNeedKrw().signum() < 0 || input.cashNeedKrw().compareTo(d("10000000000")) > 0))
            throw new IllegalArgumentException("필요 현금은 0~100억원입니다.");
        if (input.holdingMonths() != null && (input.holdingMonths() < 0 || input.holdingMonths() > 120))
            throw new IllegalArgumentException("보유 계획은 0~120개월입니다.");
        return new Conditions(Map.copyOf(result), input.targetReductionKrw(), input.cashNeedKrw(), input.holdingMonths());
    }
    public Comparison compare(ScenarioRequest request) {
        if (request == null || !data.symbols().contains(request.symbol())) throw new IllegalArgumentException("분석할 보유 종목을 선택하세요.");
        Conditions conditions = validate(request.conditions());
        List<Lot> selected = data.lots.stream().filter(l -> l.symbol().equals(request.symbol())).toList();
        int total = selected.stream().mapToInt(Lot::quantity).sum();
        int limit = allowedQuantity(total, conditions.symbols().get(request.symbol()));
        BigDecimal baseline = tax(baselineGain());
        int best = 0;
        BigDecimal bestTax = baseline;
        Integer needed = conditions.targetReductionKrw() == null ? null : (conditions.targetReductionKrw().signum() == 0 ? 0 : null);
        // 같은 최소 세액에 처음 도달한 수량을 유지해 불필요한 추가 매도를 피합니다.
        // 계좌별 원가·수수료 차이로 손익이 단순 비례하지 않을 수 있어 허용 수량을 순회합니다.
        for (int q = 1; q <= limit; q++) {
            Scenario candidate = scenario("scan", "", selected, q, true, "", conditions, null);
            if (candidate.estimatedTaxKrw().compareTo(bestTax) < 0) { best = q; bestTax = candidate.estimatedTaxKrw(); }
            if (needed == null && conditions.targetReductionKrw() != null && candidate.taxReductionKrw().compareTo(conditions.targetReductionKrw()) >= 0) needed = q;
        }
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(scenario("hold", "보유 유지", selected, 0, true, "주식을 매도하지 않습니다.", conditions, null));
        String reason = best == 0 ? "허용 범위에서 추가 매도로 세금이 감소하지 않습니다." : bestTax.signum() == 0 ? "예상 세금을 0원으로 만드는 최소 정수 수량입니다." : "허용 범위에서 가장 낮은 세금에 도달하는 최소 수량입니다. 세금 0원 달성은 불가능합니다.";
        scenarios.add(scenario("partial", "세금 감소를 위한 일부 매도", selected, best, true, reason, conditions, best == 0 ? "hold" : null));
        scenarios.add(scenario("full", "전량 매도", selected, total, limit == total, limit == total ? "전체 수량을 매도하는 가정입니다." : "적용 조건상 최대 " + limit + "주만 매도할 수 있습니다. 이 결과는 적용할 수 없습니다.", conditions, best == total ? "partial" : null));
        if (request.customQuantity() != null) {
            int q = request.customQuantity();
            if (q < 0 || q > limit) throw new IllegalArgumentException("가상 매도 수량은 0~" + limit + "주입니다.");
            String duplicate = q == 0 ? "hold" : q == best ? "partial" : q == total ? "full" : null;
            scenarios.add(scenario("custom", "직접 조절한 수량", selected, q, true, "직접 정한 가상 매도 수량입니다.", conditions, duplicate));
        }
        List<String> notices = new ArrayList<>();
        if (bestTax.signum() == 0) notices.add("예상 세금이 0원에 도달한 뒤 추가 손실을 실현해도 올해 세금 감소액은 더 늘지 않습니다.");
        if (baseline.signum() == 0) notices.add("현재 예상 세금이 이미 0원이므로 추가 손실 실현으로 올해 세금이 더 감소하지 않습니다.");
        if (conditions.targetReductionKrw() != null && needed == null) notices.add("조건을 유지하면 희망 세금 감소액 달성이 불가능합니다. 가능한 최대 감소액은 " + won(baseline.subtract(bestTax)) + "원입니다.");
        if (selected.stream().allMatch(l -> perShareTaxGain(l).signum() >= 0)) notices.add("현재 기준 원화 세무상 손실 종목이 아닙니다. 달러 평가손익과 세무상 손익은 다를 수 있습니다.");
        return new Comparison(request.symbol(), total, limit, baseline, baselineGain(), baseline.subtract(bestTax), needed, conditions, data.rule, scenarios, notices);
    }
    public int allowedQuantity(int total, Condition c) {
        if (c == null) return total;
        if (c.excluded()) return 0;
        int max = c.maxShares() == null ? total : Math.min(total, c.maxShares());
        if (c.maxPercent() != null) max = Math.min(max, d(Integer.toString(total)).multiply(c.maxPercent()).divide(d("100"), 0, RoundingMode.DOWN).intValueExact());
        return max;
    }
    public BigDecimal perShareTaxGain(Lot l) {
        return l.priceUsd().multiply(data.rule.fx()).multiply(BigDecimal.ONE.subtract(l.sellFeeRate()))
            .subtract(l.acquisitionKrw().divide(d(Integer.toString(l.quantity())), 12, RoundingMode.HALF_UP));
    }
    private Scenario scenario(String id, String title, List<Lot> lots, int q, boolean allowed, String reason, Conditions conditions, String duplicate) {
        int remaining = q;
        BigDecimal gain = BigDecimal.ZERO, fee = BigDecimal.ZERO, cashUsd = BigDecimal.ZERO, remainingValue = BigDecimal.ZERO;
        List<Sale> sales = new ArrayList<>();
        for (Lot l : lots) {
            int sold = Math.min(remaining, l.quantity()); remaining -= sold;
            BigDecimal qty = d(Integer.toString(sold));
            BigDecimal grossUsd = l.priceUsd().multiply(qty);
            BigDecimal feeUsd = grossUsd.multiply(l.sellFeeRate());
            BigDecimal feeWon = feeUsd.multiply(data.rule.fx());
            // 과거 취득원가는 현재 환율로 재환산하지 않습니다. 매수 비용은 원가에 이미 포함되어 있습니다.
            BigDecimal cost = l.acquisitionKrw().multiply(qty).divide(d(Integer.toString(l.quantity())), 12, RoundingMode.HALF_UP);
            BigDecimal lotGain = grossUsd.multiply(data.rule.fx()).subtract(feeWon).subtract(cost);
            gain = gain.add(lotGain); fee = fee.add(feeWon); cashUsd = cashUsd.add(grossUsd.subtract(feeUsd));
            remainingValue = remainingValue.add(l.priceUsd().multiply(data.rule.fx()).multiply(d(Integer.toString(l.quantity() - sold))));
            if (sold > 0) sales.add(new Sale(l.id(), l.broker(), sold, grossUsd.subtract(feeUsd), won(feeWon), won(lotGain)));
        }
        BigDecimal totalGain = baselineGain().add(gain), tax = tax(totalGain), reduction = tax(baselineGain()).subtract(tax);
        BigDecimal cash = cashUsd.multiply(data.rule.fx());
        String selectedSymbol = lots.get(0).symbol();
        BigDecimal otherHoldingsValue = data.lots.stream().filter(l -> !l.symbol().equals(selectedSymbol))
            .map(l -> l.priceUsd().multiply(data.rule.fx()).multiply(BigDecimal.valueOf(l.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<FutureValue> futures = new ArrayList<>();
        for (String change : List.of("-10", "0", "10")) {
            BigDecimal pct = d(change);
            // 매도 비용은 cash에 반영되어 있습니다. 여기서 다시 차감하지 않습니다.
            BigDecimal value = cash.add(remainingValue.multiply(BigDecimal.ONE.add(pct.divide(d("100"))))).add(otherHoldingsValue).subtract(tax);
            futures.add(new FutureValue(pct.signum() > 0 ? "+10%" : change + "%", pct, won(value)));
        }
        boolean targetMet = conditions.targetReductionKrw() == null || reduction.compareTo(conditions.targetReductionKrw()) >= 0;
        return new Scenario(id, title, q, lots.stream().mapToInt(Lot::quantity).sum() - q, allowed, reason, won(gain), won(totalGain), tax, won(reduction), won(fee), cashUsd.setScale(4, RoundingMode.HALF_UP), won(cash), won(remainingValue.add(otherHoldingsValue)), targetMet,
            conditions.targetReductionKrw() == null ? "희망 감소액 미설정" : targetMet ? "희망 감소액 충족" : "희망 감소액 미충족", duplicate, sales, futures);
    }
}
