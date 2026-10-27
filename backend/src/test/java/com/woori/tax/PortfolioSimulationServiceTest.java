package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static com.woori.tax.Models.*;
import static org.assertj.core.api.Assertions.*;

class PortfolioSimulationServiceTest {
    private PortfolioSimulationService service;
    private ScenarioService calculator;
    @BeforeEach void setup() throws Exception {
        var data = new DemoData(new ObjectMapper()); calculator = new ScenarioService(data); service = new PortfolioSimulationService(data, calculator);
    }
    @Test void firstSimulationComparesAllStocksAndStopsAtZeroTax() {
        var result = service.simulate(new SimulationRequest(Conditions.empty()));
        assertThat(result.scenarios()).hasSize(3);
        assertThat(result.baselineTaxKrw()).isEqualByComparingTo("594000");
        var max = result.scenarios().get(2);
        assertThat(max.estimatedTaxKrw()).isEqualByComparingTo("0");
        assertThat(max.taxReductionKrw()).isEqualByComparingTo("594000");
        assertThat(max.holdings()).hasSize(6);
        assertThat(max.holdings()).filteredOn(h -> h.sellQuantity() > 0)
            .allMatch(h -> java.util.Set.of("INTC", "NKE", "TSLA").contains(h.symbol()));
        assertThat(max.holdings()).filteredOn(h -> h.sellQuantity() > 0).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.scenarios().get(1).taxReductionKrw()).isGreaterThanOrEqualTo(new BigDecimal("297000")).isLessThan(max.taxReductionKrw());
    }
    @Test void excludingTeslaFindsAlternativeLossSales() {
        var conditions = new Conditions(Map.of("TSLA", new Condition(true, null, null)), null);
        var result = service.simulate(new SimulationRequest(conditions));
        for (var s : result.scenarios()) assertThat(s.holdings()).filteredOn(h -> h.symbol().equals("TSLA")).allMatch(h -> h.sellQuantity() == 0 && h.remainingQuantity() == 20);
        var max = result.scenarios().get(2);
        assertThat(max.taxReductionKrw()).isPositive().isLessThan(result.baselineTaxKrw());
        assertThat(max.holdings()).filteredOn(h -> h.symbol().equals("INTC")).extracting(HoldingPlan::sellQuantity).containsExactly(40);
        assertThat(max.holdings()).filteredOn(h -> h.symbol().equals("NKE")).extracting(HoldingPlan::sellQuantity).containsExactly(20);
    }
    @Test void combinedLimitsAndImpossibleTargetAreHonored() {
        var conditions = new Conditions(Map.of("TSLA", new Condition(true, null, null), "INTC", new Condition(false, 8, new BigDecimal("50"))), new BigDecimal("550000"));
        var result = service.simulate(new SimulationRequest(conditions));
        assertThat(result.notices()).anyMatch(s -> s.contains("도달할 수 없습니다"));
        assertThat(result.scenarios()).allMatch(s -> !s.targetMet());
        assertThat(result.scenarios().get(2).holdings()).filteredOn(h -> h.symbol().equals("INTC")).extracting(HoldingPlan::sellQuantity).containsExactly(8);
        assertThat(result.scenarios().get(2).duplicateOf()).isEqualTo("balanced");
    }
    @Test void allLossStocksExcludedProducesHonestDuplicateHoldings() {
        var conditions = new Conditions(Map.of("TSLA", new Condition(true, null, null), "INTC", new Condition(true, null, null), "NKE", new Condition(true, null, null)), null);
        var result = service.simulate(new SimulationRequest(conditions));
        assertThat(result.maxTaxReductionKrw()).isEqualByComparingTo("0");
        assertThat(result.scenarios()).allMatch(s -> s.holdings().stream().allMatch(h -> h.sellQuantity() == 0));
        assertThat(result.scenarios().get(2).duplicateOf()).isEqualTo("hold");
    }
    @Test void aggregateTaxIsComputedOnceAndFeesAreNotDoubleCharged() {
        var result = service.simulate(new SimulationRequest(new Conditions(Map.of("TSLA", new Condition(false, 10, null)), null)));
        var max = result.scenarios().get(2);
        assertThat(max.holdings()).filteredOn(h -> h.sellQuantity() > 0).hasSizeGreaterThanOrEqualTo(2);
        assertThat(max.estimatedTaxKrw()).isEqualByComparingTo(calculator.tax(calculator.baselineGain().add(max.realizedGainKrw())));
        assertThat(max.futures().get(1).netAssetsKrw()).isEqualByComparingTo(max.cashKrw().add(max.remainingValueKrw()).subtract(max.estimatedTaxKrw()));
        assertThat(max.cashKrw().add(max.remainingValueKrw()).add(max.saleFeeKrw()))
            .isEqualByComparingTo(result.scenarios().get(0).remainingValueKrw());
    }
    @Test void zeroTargetChoosesHoldAndInvalidConditionsAreRejected() {
        var result = service.simulate(new SimulationRequest(new Conditions(Map.of(), BigDecimal.ZERO)));
        assertThat(result.scenarios().get(1).duplicateOf()).isEqualTo("hold");
        assertThatThrownBy(() -> service.simulate(new SimulationRequest(new Conditions(Map.of("AAPL", new Condition(false, 21, null)), null)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void aiProposalMustRespectUserExclusionAndJavaComputesTax() {
        var conditions = new Conditions(Map.of("TSLA", new Condition(true, null, null)), null);
        var sales = Map.of("TSLA", 1, "AAPL", 0, "MSFT", 0, "NVDA", 0, "INTC", 0, "NKE", 0);
        assertThatThrownBy(() -> service.evaluateProposal(conditions, sales, "balanced", "전략", "근거", "위험"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("사용자 제한");
        var allowed = service.evaluateProposal(Conditions.empty(), sales, "balanced", "전략", "근거", "위험");
        assertThat(allowed.estimatedTaxKrw()).isLessThan(service.simulate(new SimulationRequest(Conditions.empty())).baselineTaxKrw());
        assertThat(allowed.holdings()).filteredOn(h -> h.symbol().equals("TSLA"))
            .extracting(HoldingPlan::sellQuantity).containsExactly(1);
    }
}
