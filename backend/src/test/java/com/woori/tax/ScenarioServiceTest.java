package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static com.woori.tax.DemoData.d;
import static com.woori.tax.Models.*;
import static org.assertj.core.api.Assertions.*;

class ScenarioServiceTest {
    private DemoData data;
    private ScenarioService service;
    @BeforeEach void setup() throws Exception { data = new DemoData(new ObjectMapper()); service = new ScenarioService(data); }
    @Test void deductionAndTaxReductionHaveAnnualCeiling() {
        assertThat(service.tax(d("5000000"))).isEqualByComparingTo("550000");
        assertThat(service.tax(d("5000000").subtract(d("2500000")))).isZero();
        assertThat(service.tax(d("5000000").subtract(d("4000000")))).isZero();
        assertThat(service.tax(d("-9000000"))).isZero();
    }
    @Test void confirmedWonProfitIsAggregatedAcrossBrokers() {
        var accounts = List.of(new Account("A", "A1", d("5000000")), new Account("B", "B1", d("-2000000")));
        assertThat(ScenarioService.sumRealized(accounts)).isEqualByComparingTo("3000000");
    }
    @Test void dollarLossIsNotAutomaticallyATaxLoss() {
        var amazon = data.lots.stream().filter(l -> l.symbol().equals("AMZN")).findFirst().orElseThrow();
        assertThat(amazon.priceUsd().subtract(amazon.buyUsd())).isNegative();
        assertThat(service.perShareTaxGain(amazon)).isPositive();
        var result = compare("AMZN", Conditions.empty(), null);
        assertThat(result.scenarios().get(1).sellQuantity()).isZero();
        assertThat(result.notices()).anyMatch(s -> s.contains("세무상 손실"));
    }
    @Test void partialSaleFindsMinimumIntegerQuantity() {
        var result = compare("TSLA", Conditions.empty(), null);
        var partial = result.scenarios().get(1);
        assertThat(partial.sellQuantity()).isEqualTo(29);
        assertThat(partial.estimatedTaxKrw()).isZero();
        assertThat(compare("TSLA", Conditions.empty(), 28).scenarios().get(3).estimatedTaxKrw()).isPositive();
        assertThat(partial.taxReductionKrw()).isEqualByComparingTo("550000");
    }
    @Test void excludedStockHasNoAllowedSale() {
        var conditions = new Conditions(Map.of("TSLA", new Condition(true, null, null)), null);
        var result = compare("TSLA", conditions, null);
        assertThat(result.maxAllowedQuantity()).isZero();
        assertThat(result.scenarios().stream().filter(Scenario::allowed)).allMatch(s -> s.sellQuantity() == 0 && s.sales().isEmpty());
        assertThat(result.scenarios().get(2).allowed()).isFalse();
    }
    @Test void quantityAndRatioAreBothRespected() {
        var conditions = new Conditions(Map.of("TSLA", new Condition(false, 30, d("50"))), null);
        var result = compare("TSLA", conditions, null);
        assertThat(result.maxAllowedQuantity()).isEqualTo(25);
        assertThat(result.scenarios().stream().filter(Scenario::allowed)).allMatch(s -> s.sellQuantity() <= 25);
        assertThatThrownBy(() -> compare("TSLA", conditions, 26)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.allowedQuantity(15, new Condition(false, null, d("50")))).isEqualTo(7);
    }
    @Test void impossibleTargetDoesNotRelaxConditions() {
        var conditions = new Conditions(Map.of("TSLA", new Condition(false, 10, null)), d("550000"));
        var result = compare("TSLA", conditions, null);
        assertThat(result.conditions()).isEqualTo(conditions);
        assertThat(result.maxAllowedQuantity()).isEqualTo(10);
        assertThat(result.requiredForTarget()).isNull();
        assertThat(result.notices()).anyMatch(n -> n.contains("불가능"));
        assertThat(result.scenarios().get(1).targetMet()).isFalse();
    }
    @Test void pastBuyCostAndAdditionalSellFeeAreNotDoubleCounted() {
        var result = compare("TSLA", Conditions.empty(), 1);
        var custom = result.scenarios().get(3);
        assertThat(custom.saleFeeKrw()).isEqualByComparingTo("828");
        assertThat(custom.realizedGainKrw()).isEqualByComparingTo("-86628");
        assertThat(custom.cashKrw()).isEqualByComparingTo("330372");
        var expected = custom.cashKrw().add(custom.remainingValueKrw()).subtract(custom.estimatedTaxKrw());
        assertThat(custom.futures().get(1).netAssetsKrw()).isEqualByComparingTo(expected);
    }
    @Test void completeSaleKeepsCashFixedAcrossFuturePriceAssumptions() {
        var full = compare("TSLA", Conditions.empty(), null).scenarios().get(2);
        assertThat(full.futures().stream().map(FutureValue::netAssetsKrw).distinct()).hasSize(1);
        assertThat(full.sales()).hasSize(2);
        assertThat(full.sales().get(0).quantity()).isEqualTo(30);
        assertThat(full.sales().get(1).quantity()).isEqualTo(20);
    }
    @Test void unknownAndInvalidConditionsAreRejected() {
        assertThatThrownBy(() -> compare("UNKNOWN", Conditions.empty(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new Conditions(Map.of("TSLA", new Condition(false, 51, null)), null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new Conditions(Map.of("TSLA", new Condition(false, null, d("101"))), null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(new Conditions(Map.of(), d("-1")))).isInstanceOf(IllegalArgumentException.class);
    }
    private Comparison compare(String symbol, Conditions conditions, Integer qty) { return service.compare(new ScenarioRequest(symbol, conditions, qty)); }
}
