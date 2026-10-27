package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.woori.tax.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LiveAssessmentServiceTest {
    @Test void groundedAiProposalsAreCalculatedAndKeptWithinPortfolio() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DemoData data = new DemoData(mapper);
        PortfolioSimulationService simulation = new PortfolioSimulationService(data, new ScenarioService(data));
        OpenAiService ai = mock(OpenAiService.class);
        when(ai.search(anyString(), anyString())).thenReturn(new OpenAiService.GroundedResult("TSLA 실적발표 일정은 확인 필요. 매도 전 보유 기회를 비교.",
            List.of(new OpenAiService.GroundingSource("live-0", "Investor Relations", "https://example.com/ir")),
            List.of(new OpenAiService.GroundingClaim("TSLA 실적발표 일정은 확인 필요.", List.of("live-0")))));
        when(ai.json(anyString(), anyString(), anyString())).thenReturn(mapper.readTree("""
            {"proposals":[
              {"id":"balanced","title":"발표 전 보유","reason":"실적 일정 확인 전 보유 기회를 우선합니다.","tradeoff":"절세는 발생하지 않습니다.","sourceIds":["live-0"],"sales":[{"symbol":"TSLA","quantity":0},{"symbol":"AAPL","quantity":0},{"symbol":"NVDA","quantity":0},{"symbol":"AMZN","quantity":0}],"stockReasons":[{"symbol":"TSLA","reason":"발표 전 보유"},{"symbol":"AAPL","reason":"계속 보유"},{"symbol":"NVDA","reason":"계속 보유"},{"symbol":"AMZN","reason":"계속 보유"}]},
              {"id":"alternative","title":"소량 손실 실현","reason":"일부 손실만 실현합니다.","tradeoff":"상승 참여 수량이 줄어듭니다.","sourceIds":["live-0"],"sales":[{"symbol":"TSLA","quantity":1},{"symbol":"AAPL","quantity":0},{"symbol":"NVDA","quantity":0},{"symbol":"AMZN","quantity":0}],"stockReasons":[{"symbol":"TSLA","reason":"소량 매도"},{"symbol":"AAPL","reason":"계속 보유"},{"symbol":"NVDA","reason":"계속 보유"},{"symbol":"AMZN","reason":"계속 보유"}]}
            ]}
            """));
        var original = simulation.simulate(new SimulationRequest(Conditions.empty()));
        var assessment = new LiveAssessmentService(simulation, ai, data, mapper).assess(new SimulationRequest(Conditions.empty()));
        assertThat(assessment.mode()).isEqualTo("live");
        assertThat(assessment.decision().changedFromDefault()).isTrue();
        assertThat(assessment.comparison().scenarios().get(1).holdings()).extracting(HoldingPlan::sellQuantity).containsOnly(0);
        assertThat(assessment.comparison().scenarios().get(1).estimatedTaxKrw()).isEqualByComparingTo(original.baselineTaxKrw());
        assertThat(assessment.comparison().scenarios().get(2).holdings()).extracting(HoldingPlan::sellQuantity).containsExactly(1, 0, 0, 0);
        assertThat(assessment.comparison().scenarios().get(2).estimatedTaxKrw()).isLessThan(original.baselineTaxKrw());
        assertThat(assessment.scenarioSourceIds()).containsEntry("balanced", List.of("live-0"))
            .containsEntry("alternative", List.of("live-0"));
        assertThat(assessment.stockReasons().get("alternative")).containsEntry("TSLA", "소량 매도");
    }

    @Test void inventedSourceOrMissingGroundingKeepsCalculatedResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DemoData data = new DemoData(mapper);
        PortfolioSimulationService simulation = new PortfolioSimulationService(data, new ScenarioService(data));
        OpenAiService ai = mock(OpenAiService.class);
        when(ai.search(anyString(), anyString())).thenReturn(new OpenAiService.GroundedResult("검색", List.of(
            new OpenAiService.GroundingSource("live-0", "IR", "https://example.com")),
            List.of(new OpenAiService.GroundingClaim("검색된 자료", List.of("live-0")))));
        when(ai.json(anyString(), anyString(), anyString())).thenReturn(mapper.readTree("""
            {"proposals":[{"id":"balanced","title":"보유","reason":"이유","tradeoff":"기회","sourceIds":["invented"],"sales":[]},
              {"id":"alternative","title":"매도","reason":"이유","tradeoff":"위험","sourceIds":["live-0"],"sales":[]}]}
            """));
        var result = new LiveAssessmentService(simulation, ai, data, mapper).assess(new SimulationRequest(Conditions.empty()));
        assertThat(result.mode()).isEqualTo("unavailable");
        assertThat(result.comparison()).isEqualTo(simulation.simulate(new SimulationRequest(Conditions.empty())));
    }
    @Test void outOfRangeAiQuantityFallsBackToCalculatedResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DemoData data = new DemoData(mapper);
        PortfolioSimulationService simulation = new PortfolioSimulationService(data, new ScenarioService(data));
        OpenAiService ai = mock(OpenAiService.class);
        when(ai.search(anyString(), anyString())).thenReturn(new OpenAiService.GroundedResult("검색",
            List.of(new OpenAiService.GroundingSource("live-0", "IR", "https://example.com")),
            List.of(new OpenAiService.GroundingClaim("검색된 자료", List.of("live-0")))));
        when(ai.json(anyString(), anyString(), anyString())).thenReturn(mapper.readTree("""
            {"proposals":[
              {"id":"balanced","title":"과다 매도","reason":"이유","tradeoff":"위험","sourceIds":["live-0"],"sales":[{"symbol":"TSLA","quantity":999},{"symbol":"AAPL","quantity":0},{"symbol":"NVDA","quantity":0},{"symbol":"AMZN","quantity":0}]},
              {"id":"alternative","title":"보유","reason":"이유","tradeoff":"기회","sourceIds":["live-0"],"sales":[{"symbol":"TSLA","quantity":0},{"symbol":"AAPL","quantity":0},{"symbol":"NVDA","quantity":0},{"symbol":"AMZN","quantity":0}]}
            ]}
            """));
        var result = new LiveAssessmentService(simulation, ai, data, mapper).assess(new SimulationRequest(Conditions.empty()));
        assertThat(result.mode()).isEqualTo("unavailable");
        assertThat(result.comparison()).isEqualTo(simulation.simulate(new SimulationRequest(Conditions.empty())));
    }
}
