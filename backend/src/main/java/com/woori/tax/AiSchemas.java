package com.woori.tax;

import java.util.List;
import java.util.Map;

/** Strict Responses API formats for the application's non-search AI calls. */
final class AiSchemas {
    private AiSchemas() {}

    private static Map<String, Object> value(String type) { return Map.of("type", type); }
    private static Map<String, Object> nullable(String type) { return Map.of("type", List.of(type, "null")); }
    private static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }
    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties,
            "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }

    private static final Map<String, Object> CONDITION_PATCH = object(Map.of(
        "symbol", value("string"), "excluded", nullable("boolean"),
        "maxShares", nullable("integer"), "maxPercent", nullable("number")));

    static final Map<String, Object> CONDITION_EXTRACTION = object(Map.of(
        "changes", array(CONDITION_PATCH),
        "targetReductionKrw", nullable("number"),
        "cashNeedKrw", nullable("number"),
        "holdingMonths", nullable("integer"),
        "questions", array(value("string")),
        "message", value("string"),
        "pendingQuestion", Map.of("anyOf", List.of(CONDITION_PATCH, value("null")))));

    private static final Map<String, Object> PROPOSAL = object(Map.of(
        "id", value("string"), "title", value("string"),
        "reason", value("string"), "tradeoff", value("string"),
        "sourceIds", array(value("string")),
        "sales", array(object(Map.of("symbol", value("string"), "quantity", value("integer")))),
        "stockReasons", array(object(Map.of("symbol", value("string"), "reason", value("string"))))));

    static final Map<String, Object> PORTFOLIO_PROPOSALS = object(Map.of(
        "proposals", array(PROPOSAL)));

    static final Map<String, Object> PORTFOLIO_EXPLANATION = object(Map.of(
        "scenarios", array(object(Map.of("id", value("string"), "text", value("string"),
            "sourceIds", array(value("string")))))));

    static final Map<String, Object> SINGLE_EXPLANATION = object(Map.of(
        "comparison", value("string"), "positive", value("string"),
        "negative", value("string"), "limitations", value("string"),
        "sourceIds", array(value("string"))));
}
