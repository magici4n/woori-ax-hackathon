package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import static com.woori.tax.DemoData.d;
import static com.woori.tax.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
    private OpenAiService ai;
    private AssistantService assistant;
    private EvidenceService evidence;
    private ScenarioService calculations;
    private ObjectMapper mapper;
    @BeforeEach void setup() throws Exception {
        mapper = new ObjectMapper(); ai = mock(OpenAiService.class);
        var data = new DemoData(mapper); calculations = new ScenarioService(data); evidence = new EvidenceService(mapper);
        assistant = new AssistantService(ai, mapper, calculations, evidence, data);
    }
    @Test void newUtterancePreservesUnmentionedConditions() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"TSLA","excluded":null,"maxShares":null,"maxPercent":50}],"targetReductionKrw":null,"questions":[],"message":"절반 제한을 확인하세요."}
            """));
        var old = new Conditions(Map.of("AAPL", new Condition(true, null, null)), d("300000"));
        var response = assistant.extract(new ExtractRequest("테슬라는 절반까지", old));
        var proposed = (Conditions) response.get("conditions");
        assertThat(proposed.symbols().get("AAPL").excluded()).isTrue();
        assertThat(proposed.symbols().get("TSLA").maxPercent()).isEqualByComparingTo("50");
        assertThat(proposed.targetReductionKrw()).isEqualByComparingTo("300000");
        assertThat(old.symbols()).doesNotContainKey("TSLA");
        assertThat(response.get("canApply")).isEqualTo(true);
    }
    @Test void explicitCashAndHoldingGoalsAreExtractedWithoutErasingExistingGoals() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[],"targetReductionKrw":null,"cashNeedKrw":5000000,"holdingMonths":18,
             "questions":[],"message":"현금과 보유 기간을 조건에 반영했어요."}
            """));
        var old = new Conditions(Map.of(), d("300000"));
        var response = assistant.extract(new ExtractRequest("현금 500만원이 필요하고 18개월 보유할래", old));
        var proposed = (Conditions) response.get("conditions");
        assertThat(proposed.cashNeedKrw()).isEqualByComparingTo("5000000");
        assertThat(proposed.holdingMonths()).isEqualTo(18);
        assertThat(proposed.targetReductionKrw()).isEqualByComparingTo("300000");
        assertThat(response.get("canApply")).isEqualTo(true);
    }
    @Test void ambiguousIntentCannotApplyChanges() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"TSLA","excluded":true}],"targetReductionKrw":null,"questions":["매도에서 제외할까요?"],"message":"보유 의사를 확인해 주세요."}
            """));
        var result = assistant.extract(new ExtractRequest("테슬라 장기투자 중", Conditions.empty()));
        assertThat(result.get("conditions")).isEqualTo(Conditions.empty());
        assertThat(result.get("canApply")).isEqualTo(false);
    }
    @Test void outOfRangeQuantityCannotChangeExistingConstraints() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"TSLA","maxShares":40}],"targetReductionKrw":null,"questions":[],"message":"수량 변경"}
            """));
        var old = new Conditions(Map.of("TSLA", new Condition(false, 10, null)), null);
        var result = assistant.extract(new ExtractRequest("더 팔아", old));
        assertThat(result.get("conditions")).isEqualTo(old);
        assertThat(result.get("canApply")).isEqualTo(false);
    }
    @Test void exclusionCanBeLiftedWithoutChangingOtherLimitsOrStocks() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"TSLA","excluded":false}],"questions":[],"message":"테슬라의 매도 제외만 해제했어요."}
            """));
        var old = new Conditions(Map.of("TSLA", new Condition(true, 5, d("50")),
            "NKE", new Condition(true, null, null)), d("200000"));
        var result = assistant.extract(new ExtractRequest("테슬라 매도 제외만 해제해줘", old));
        var next = (Conditions)result.get("conditions");
        assertThat(next.symbols().get("TSLA")).isEqualTo(new Condition(false, 5, d("50")));
        assertThat(next.symbols().get("NKE")).isEqualTo(old.symbols().get("NKE"));
        assertThat(next.targetReductionKrw()).isEqualByComparingTo("200000");
        assertThat(result.get("canApply")).isEqualTo(true);
        assertThat(old.symbols().get("TSLA").excluded()).isTrue();
    }
    @Test void shareAndPercentageLimitsCanBeIncreasedWithinHoldings() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"TSLA","maxShares":15},{"symbol":"NKE","maxPercent":75}],
             "questions":[],"message":"테슬라는 15주, 나이키는 75%까지 변경했어요."}
            """));
        var old = new Conditions(Map.of("TSLA", new Condition(false, 10, null),
            "NKE", new Condition(false, null, d("25")), "INTC", new Condition(true, null, null)), null);
        var result = assistant.extract(new ExtractRequest("테슬라는 15주까지, 나이키는 75%까지 팔 수 있어", old));
        var next = (Conditions)result.get("conditions");
        assertThat(next.symbols().get("TSLA").maxShares()).isEqualTo(15);
        assertThat(next.symbols().get("NKE").maxPercent()).isEqualByComparingTo("75");
        assertThat(next.symbols().get("INTC").excluded()).isTrue();
        assertThat(result.get("canApply")).isEqualTo(true);
    }
    @Test void allSaleLimitsCanBeReleasedWithinHoldingQuantity() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"TSLA","excluded":false,"maxShares":20,"maxPercent":100}],
             "questions":[],"message":"테슬라의 제외와 수량·비율 제한을 해제했어요."}
            """));
        var old = new Conditions(Map.of("TSLA", new Condition(true, 5, d("25"))), null);
        var result = assistant.extract(new ExtractRequest("테슬라의 매도 제한을 모두 풀고 전량까지 비교해줘", old));
        var next = (Conditions)result.get("conditions");
        assertThat(calculations.allowedQuantity(20, next.symbols().get("TSLA"))).isEqualTo(20);
        assertThat(result.get("canApply")).isEqualTo(true);
    }
    @Test void ambiguousReleaseWaitsForConfirmationAndCanBeConfirmedOrRejected() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[],"questions":["테슬라의 매도 제외를 해제할까요?"],"message":"변경 의사를 확인할게요.",
             "pendingQuestion":{"symbol":"TSLA","excluded":false}}
            """));
        var old = new Conditions(Map.of("TSLA", new Condition(true, null, null)), null);
        var first = assistant.extract(new ExtractRequest("테슬라도 팔아볼까", old, "TSLA", null));
        assertThat(first.get("canApply")).isEqualTo(false);
        assertThat(first.get("conditions")).isEqualTo(old);
        var confirmed = assistant.extract(new ExtractRequest("네", old, "TSLA", (String)first.get("conversationId")));
        assertThat(((Conditions)confirmed.get("conditions")).symbols().get("TSLA").excluded()).isFalse();
        assertThat(confirmed.get("canApply")).isEqualTo(true);
        var second = assistant.extract(new ExtractRequest("테슬라도 팔아볼까", old, "TSLA", null));
        var rejected = assistant.extract(new ExtractRequest("아니요", old, "TSLA", (String)second.get("conversationId")));
        assertThat(rejected.get("conditions")).isEqualTo(old);
        assertThat(rejected.get("canApply")).isEqualTo(false);
        verify(ai, times(2)).jsonSchema(anyString(), anyString(), anyString(), anyMap());
    }
    @Test void onlyIntelExcludedCanUpdateAllSixStocksInOneMessage() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"AAPL","excluded":false},{"symbol":"MSFT","excluded":false},
             {"symbol":"NVDA","excluded":false},{"symbol":"INTC","excluded":true},
             {"symbol":"NKE","excluded":false},{"symbol":"TSLA","excluded":false}],
             "questions":[],"message":"인텔만 제외하고 나머지는 매도 가능하게 변경했어요."}
            """));
        var old = new Conditions(Map.of("TSLA", new Condition(true, null, null),
            "NKE", new Condition(true, null, null)), null);
        var result = assistant.extract(new ExtractRequest("인텔만 제외하고 나머지 종목은 모두 매도 가능하게 해줘", old));
        var next = (Conditions)result.get("conditions");
        assertThat(next.symbols()).hasSize(6);
        assertThat(next.symbols().get("INTC").excluded()).isTrue();
        assertThat(next.symbols().entrySet()).filteredOn(entry -> !entry.getKey().equals("INTC"))
            .allMatch(entry -> !entry.getValue().excluded());
        assertThat(result.get("canApply")).isEqualTo(true);
    }
    @Test void providerFailureLeavesCalculationAndManualConditionsWorking() {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenThrow(new OpenAiService.AiUnavailable("quota"));
        var manual = new Conditions(Map.of("TSLA", new Condition(false, 5, null)), null);
        var chat = assistant.extract(new ExtractRequest("반만", manual));
        assertThat(chat.get("mode")).isEqualTo("unavailable");
        assertThat(chat.get("conditions")).isEqualTo(manual);
        assertThat(calculations.compare(new ScenarioRequest("TSLA", manual, 5)).scenarios().get(3).sellQuantity()).isEqualTo(5);
        assertThat(assistant.explain(new ExplainRequest("NVDA", manual, null)).get("mode")).isEqualTo("unavailable");
    }
    @Test void fabricatedCitationIsRejected() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"comparison":"비교", "positive":"참고", "negative":"위험", "limitations":"한계", "sourceIds":["INVENTED"]}
            """));
        assertThat(assistant.explain(new ExplainRequest("TSLA", Conditions.empty(), null)).get("mode")).isEqualTo("unavailable");
    }
    @Test void evidenceFiltersSymbolsDatesAndDocumentTypes() {
        var tesla = evidence.search("TSLA", LocalDate.of(2026, 10, 26), Set.of("filing"));
        assertThat(tesla).extracting(Source::id).containsExactly("TSLA-2026Q2");
        assertThat(evidence.search("TSLA", LocalDate.of(2026, 1, 1), Set.of("filing"))).isEmpty();
        assertThat(evidence.search("NVDA", LocalDate.of(2026, 10, 26), Set.of("company", "filing"))).extracting(Source::id).containsExactly("NVDA-2027Q2");
        assertThat(evidence.search("NVDA", LocalDate.of(2026, 1, 1), Set.of("company", "filing"))).isEmpty();
    }
    private void mockExclusionQuestion() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[],"targetReductionKrw":null,"questions":["테슬라를 매도에서 제외할까요?"],
             "message":"보유 의사를 확인해요.","pendingQuestion":{"symbol":"TSLA","excluded":true}}
            """));
    }
    @Test void followupYesUsesStructuredPendingQuestionWithoutAnotherAiCall() throws Exception {
        mockExclusionQuestion();
        var old = new Conditions(Map.of("AAPL", new Condition(true, null, null)), null);
        var first = assistant.extract(new ExtractRequest("테슬라 보유 의사를 확인하고 싶어", old, "TSLA", null));
        assertThat(first.get("canApply")).isEqualTo(false);
        var response = assistant.extract(new ExtractRequest("응, 그 종목 제외해줘", old, "TSLA", (String)first.get("conversationId")));
        assertThat(response.get("mode")).isEqualTo("confirmation");
        assertThat(response.get("canApply")).isEqualTo(true);
        var proposed = (Conditions)response.get("conditions");
        assertThat(proposed.symbols().get("TSLA").excluded()).isTrue();
        assertThat(proposed.symbols().get("AAPL").excluded()).isTrue();
        assertThat(old.symbols()).doesNotContainKey("TSLA");
        verify(ai, times(1)).jsonSchema(anyString(), anyString(), anyString(), anyMap());
    }
    @Test void switchingSelectedStockInvalidatesOldYesAnswer() throws Exception {
        mockExclusionQuestion();
        var first = assistant.extract(new ExtractRequest("테슬라 보유 의사를 확인해줘", Conditions.empty(), "TSLA", null));
        var next = assistant.extract(new ExtractRequest("응", Conditions.empty(), "AAPL", (String)first.get("conversationId")));
        assertThat(next.get("canApply")).isEqualTo(false);
        assertThat(next.get("conditions")).isEqualTo(Conditions.empty());
        verify(ai, times(1)).jsonSchema(anyString(), anyString(), anyString(), anyMap());
    }
    @Test void noCancelsQuestionWithoutChangingExistingConditions() throws Exception {
        mockExclusionQuestion();
        var first = assistant.extract(new ExtractRequest("테슬라 보유 의사를 확인해줘", Conditions.empty(), "TSLA", null));
        var next = assistant.extract(new ExtractRequest("아니요", Conditions.empty(), "TSLA", (String)first.get("conversationId")));
        assertThat(next.get("canApply")).isEqualTo(false);
        assertThat(next.get("pendingQuestion")).isNull();
        assertThat(next.get("conditions")).isEqualTo(Conditions.empty());
    }
    @Test void noQuestionMeansShortYesCannotInventACondition() {
        var result = assistant.extract(new ExtractRequest("응", Conditions.empty(), "TSLA", null));
        assertThat(result.get("canApply")).isEqualTo(false);
        verifyNoInteractions(ai);
    }
    @Test void selectedQuantityAndRecentConversationAreProvidedToModel() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"TSLA","maxPercent":50}],"targetReductionKrw":null,"questions":[],"message":"절반까지로 이해했어요."}
            """));
        var first = assistant.extract(new ExtractRequest("이 종목 절반까지", Conditions.empty(), "TSLA", null));
        assistant.extract(new ExtractRequest("목표는 30만원", Conditions.empty(), "TSLA", (String)first.get("conversationId")));
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ai, times(2)).jsonSchema(anyString(), captor.capture(), anyString(), anyMap());
        var input = mapper.readTree(captor.getAllValues().get(1));
        assertThat(input.path("currentSelection").path("symbol").asText()).isEqualTo("TSLA");
        assertThat(input.path("currentSelection").path("quantity").asInt()).isEqualTo(20);
        assertThat(input.path("recentMessages").size()).isEqualTo(2);
        assertThat(input.path("recentMessages").get(0).path("text").asText()).contains("이 종목");
    }
    @Test void investmentAdviceQuestionIsRedirectedToSupportedComparison() {
        var result = assistant.extract(new ExtractRequest("엔비디아 지금 사도 돼?", Conditions.empty(), "NVDA", null));
        assertThat(result.get("mode")).isEqualTo("guidance");
        assertThat(result.get("message").toString()).contains("추천", "비교");
        assertThat(result.get("canApply")).isEqualTo(false);
        verifyNoInteractions(ai);
    }
    @Test void simpleLongTermStatementAsksWithoutAiOrAutomaticExclusion() {
        var question = assistant.extract(new ExtractRequest("이 종목은 장기투자 중이야.", Conditions.empty(), "TSLA", null));
        assertThat(question.get("pendingQuestion")).isNotNull();
        assertThat(question.get("canApply")).isEqualTo(false);
        assertThat(question.get("conditions")).isEqualTo(Conditions.empty());
        var confirmed = assistant.extract(new ExtractRequest("네", Conditions.empty(), "TSLA", (String)question.get("conversationId")));
        assertThat(confirmed.get("canApply")).isEqualTo(true);
        verifyNoInteractions(ai);
    }

    @Test void portfolioExplanationRejectsInventedSourcesAndDuplicateScenarios() throws Exception {
        var result = new PortfolioSimulationService(new DemoData(mapper), calculations).simulate(new SimulationRequest(Conditions.empty()));
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"scenarios":[{"id":"hold","text":"test","sourceIds":["FAKE"]},{"id":"balanced","text":"test","sourceIds":["NTS-CALC"]},{"id":"tax","text":"test","sourceIds":["NTS-CALC"]}]}
            """));
        assertThat(assistant.explainPortfolio(result).get("mode")).isEqualTo("unavailable");
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"scenarios":[{"id":"hold","text":"test","sourceIds":["NTS-CALC"]},{"id":"hold","text":"test","sourceIds":["NTS-CALC"]},{"id":"tax","text":"test","sourceIds":["NTS-CALC"]}]}
            """));
        assertThat(assistant.explainPortfolio(result).get("mode")).isEqualTo("unavailable");
    }

    @Test void consecutiveDraftMessagesPreservePriorExclusion() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[{"symbol":"AAPL","maxPercent":50}],"targetReductionKrw":null,"questions":[],"message":"애플 한도도 추가했어요."}
            """));
        var draft = new Conditions(Map.of("TSLA", new Condition(true, null, null)), null);
        var result = assistant.extract(new ExtractRequest("애플도 반만", draft, "TSLA", null));
        var next = (Conditions)result.get("conditions");
        assertThat(next.symbols().get("TSLA").excluded()).isTrue();
        assertThat(next.symbols().get("AAPL").maxPercent()).isEqualByComparingTo("50");
    }
    @Test void riskPreferenceQuestionInMessageStillRequiresClarification() throws Exception {
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"changes":[],"questions":[],"message":"보유 비중을 유지하고 싶은 건가요?","pendingQuestion":null}
            """));
        var draft = new Conditions(Map.of("TSLA", new Condition(true, null, null)), null);
        var response = assistant.extract(new ExtractRequest("공격적으로 투자하고 싶어", draft, "TSLA", null));
        assertThat((java.util.List<?>)response.get("questions")).hasSize(1);
        assertThat(response.get("conditions")).isEqualTo(draft);
        assertThat(response.get("canApply")).isEqualTo(false);
    }

}
