package com.woori.tax;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}
    public record Lot(String id, String broker, String account, String symbol, String name,
                      int quantity, BigDecimal buyUsd, BigDecimal acquisitionKrw,
                      BigDecimal priceUsd, BigDecimal sellFeeRate, BigDecimal buyFeeKrw) {}
    public record Account(String broker, String account, BigDecimal realizedKrw) {}
    public record Rule(int year, BigDecimal deductionKrw, BigDecimal nationalRate,
                       BigDecimal localRate, BigDecimal fx, String asOf, String rounding,
                       String settlementAssumption, List<String> assumptions) {
        public BigDecimal getCombinedRatePercent() { return nationalRate.add(localRate).movePointRight(2); }
    }
    public record Condition(boolean excluded, Integer maxShares, BigDecimal maxPercent) {}
    public record Conditions(Map<String, Condition> symbols, BigDecimal targetReductionKrw,
                             BigDecimal cashNeedKrw, Integer holdingMonths) {
        public Conditions(Map<String, Condition> symbols, BigDecimal targetReductionKrw) {
            this(symbols, targetReductionKrw, null, null);
        }
        public static Conditions empty() { return new Conditions(Map.of(), null, null, null); }
    }
    public record ScenarioRequest(String symbol, Conditions conditions, Integer customQuantity) {}
    public record FutureValue(String label, BigDecimal changePercent, BigDecimal netAssetsKrw) {}
    public record Sale(String lotId, String broker, int quantity, BigDecimal proceedsUsd,
                       BigDecimal feeKrw, BigDecimal gainKrw) {}
    public record Scenario(String id, String title, int sellQuantity, int remainingQuantity,
                           boolean allowed, String reason, BigDecimal realizedGainKrw,
                           BigDecimal totalRealizedKrw, BigDecimal estimatedTaxKrw,
                           BigDecimal taxReductionKrw, BigDecimal saleFeeKrw,
                           BigDecimal cashUsd, BigDecimal cashKrw, BigDecimal remainingValueKrw,
                           boolean targetMet, String targetMessage, String duplicateOf,
                           List<Sale> sales, List<FutureValue> futures) {}
    public record Comparison(String symbol, int totalQuantity, int maxAllowedQuantity,
                             BigDecimal baselineTaxKrw, BigDecimal baselineRealizedKrw,
                             BigDecimal maxTaxReductionKrw, Integer requiredForTarget,
                             Conditions conditions, Rule rule, List<Scenario> scenarios,
                             List<String> notices) {}
    public record Source(String id, String title, String url, String publishedAt,
                         String collectedAt, List<String> symbols, String kind, String summary) {}
    public record ExtractRequest(String message, Conditions existing, String selectedSymbol, String conversationId) {
        public ExtractRequest(String message, Conditions existing) { this(message, existing, null, null); }
    }
    public record ConversationMessage(String role, String text) {}
    public record PendingQuestion(String id, ConditionPatch proposal, String question, String contextSymbol) {}
    public record Extracted(List<ConditionPatch> changes, BigDecimal targetReductionKrw,
                            List<String> questions, String message, String mode) {}
    public record ConditionPatch(String symbol, Boolean excluded, Integer maxShares, BigDecimal maxPercent) {}
    public record ExplainRequest(String symbol, Conditions conditions, Integer customQuantity) {}
    public record SimulationRequest(Conditions conditions, String decisionPriority, String conversationId) {
        public SimulationRequest(Conditions conditions) { this(conditions, null, null); }
        public SimulationRequest(Conditions conditions, String decisionPriority) { this(conditions, decisionPriority, null); }
    }
    public record HoldingPlan(String symbol, String name, int quantity, int sellQuantity, int remainingQuantity,
                              BigDecimal realizedGainKrw, String reason) {}
    public record PortfolioScenario(String id, String title, String reason, String tradeoff,
                                    BigDecimal estimatedTaxKrw, BigDecimal taxReductionKrw,
                                    BigDecimal realizedGainKrw, BigDecimal saleFeeKrw, BigDecimal cashKrw,
                                    BigDecimal remainingValueKrw, BigDecimal retainedPercent,
                                    boolean targetMet, String duplicateOf, List<HoldingPlan> holdings,
                                    List<FutureValue> futures) {}
    public record PortfolioComparison(BigDecimal baselineTaxKrw, BigDecimal maxTaxReductionKrw,
                                      Conditions conditions, Rule rule, List<PortfolioScenario> scenarios,
                                      List<String> notices) {}
    public record ResearchNote(String symbol, String sourceId, String positive, String risk, String relevance) {}
}
