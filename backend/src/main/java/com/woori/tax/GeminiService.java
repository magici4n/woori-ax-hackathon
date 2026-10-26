package com.woori.tax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

@Service
public class GeminiService {
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String key, model;
    private final int maxCalls, maxOutput;
    private final boolean paid;
    private final BigDecimal budget, inputPrice, outputPrice;
    private final Path ledger = Path.of("data", "ai-usage.json");
    private final Map<String, JsonNode> cache = new LinkedHashMap<>();
    private int calls;
    private long inputTokens, outputTokens;
    private BigDecimal estimatedCost = BigDecimal.ZERO;
    private boolean ledgerAvailable = true;
    public GeminiService(ObjectMapper mapper, @Value("${app.gemini.key}") String key,
                         @Value("${app.gemini.model}") String model, @Value("${app.gemini.max-calls}") int maxCalls,
                         @Value("${app.gemini.paid}") boolean paid, @Value("${app.gemini.budget-usd}") BigDecimal budget,
                         @Value("${app.gemini.input-per-million}") BigDecimal inputPrice,
                         @Value("${app.gemini.output-per-million}") BigDecimal outputPrice,
                         @Value("${app.gemini.max-output-tokens}") int maxOutput) {
        this.mapper = mapper; this.key = key; this.model = model; this.maxCalls = maxCalls; this.paid = paid;
        this.budget = budget; this.inputPrice = inputPrice; this.outputPrice = outputPrice; this.maxOutput = maxOutput;
        if (!model.matches("[a-zA-Z0-9.\\-]{1,100}") || maxOutput < 100 || maxOutput > 2000 || maxCalls < 0 || budget.signum() < 0 || inputPrice.signum() <= 0 || outputPrice.signum() <= 0) throw new IllegalArgumentException("Gemini 환경변수를 확인하세요.");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
        factory.setReadTimeout(Duration.ofSeconds(25));
        client = RestClient.builder().requestFactory(factory).baseUrl("https://generativelanguage.googleapis.com").build();
        if (Files.exists(ledger)) {
            try {
                JsonNode saved = mapper.readTree(ledger.toFile());
                if (!saved.path("calls").isIntegralNumber() || !saved.path("inputTokens").isIntegralNumber() || !saved.path("outputTokens").isIntegralNumber() || !saved.path("estimatedCostUsd").isNumber()) throw new IllegalArgumentException("usage fields missing");
                calls = saved.path("calls").intValue(); inputTokens = saved.path("inputTokens").longValue(); outputTokens = saved.path("outputTokens").longValue();
                estimatedCost = saved.path("estimatedCostUsd").decimalValue();
                if (calls < 0 || inputTokens < 0 || outputTokens < 0 || estimatedCost.signum() < 0) ledgerAvailable = false;
            } catch (Exception ignored) { ledgerAvailable = false; }
        }
    }
    public synchronized Map<String, Object> status() {
        return Map.of("configured", !key.isBlank(), "model", model, "paid", paid, "calls", calls,
            "maxCalls", maxCalls, "inputTokens", inputTokens, "outputTokens", outputTokens,
            "estimatedCostUsd", estimatedCost, "budgetUsd", budget, "ledgerAvailable", ledgerAvailable);
    }
    // 호출 수와 상한 비용을 먼저 파일로 예약합니다. 실패·프로세스 종료 때도 예약은 남아 과소 추정을 줄입니다.
    public synchronized JsonNode json(String system, String prompt, String version) {
        String cacheKey = model + version + system + prompt;
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey).deepCopy();
        if (key.isBlank()) throw new AiUnavailable("Gemini 키가 설정되지 않았습니다. 수동 조건 입력과 계산을 사용할 수 있습니다.");
        if (!ledgerAvailable) throw new AiUnavailable("사용량 기록 파일을 확인해야 합니다. 계산과 수동 조건은 계속 사용할 수 있습니다.");
        if (calls >= maxCalls) throw new AiUnavailable("앱의 AI 호출 한도에 도달했습니다. 계산은 계속 사용할 수 있습니다.");
        if (prompt.length() + system.length() > 16000) throw new AiUnavailable("AI 입력 길이 한도를 초과했습니다.");
        long reservedInput = (system + prompt).getBytes(StandardCharsets.UTF_8).length + 200;
        BigDecimal reserved = cost(reservedInput, maxOutput);
        if (estimatedCost.add(reserved).compareTo(budget.min(new BigDecimal("20"))) > 0) throw new AiUnavailable("앱의 AI 예산 한도에 도달했습니다.");
        calls++; estimatedCost = estimatedCost.add(reserved);
        persist();
        try {
            Map<String,Object> generation = new HashMap<>(Map.of("temperature", 0.1, "maxOutputTokens", maxOutput, "responseMimeType", "application/json"));
            if (model.equals("gemini-2.5-flash")) generation.put("thinkingConfig", Map.of("thinkingBudget", 0));
            JsonNode response = client.post().uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", key).body(Map.of("systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))), "generationConfig", generation))
                .retrieve().body(JsonNode.class);
            if (response == null || !response.path("candidates").path(0).path("finishReason").asText().equals("STOP")) throw new AiUnavailable("AI 응답이 완성되지 않았습니다. 다시 요청하거나 수동으로 조건을 설정하세요.");
            JsonNode usage = response.path("usageMetadata");
            long in = usage.path("promptTokenCount").asLong(reservedInput);
            long out = usage.path("candidatesTokenCount").asLong(maxOutput) + usage.path("thoughtsTokenCount").asLong(0);
            inputTokens += in; outputTokens += out;
            // 가격표 가정에 따른 추정치입니다. 실제 결제 내역은 제공자 콘솔에서 확인해야 합니다.
            estimatedCost = estimatedCost.subtract(reserved).add(cost(in, out));
            persist();
            StringBuilder output = new StringBuilder();
            for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) output.append(part.path("text").asText());
            JsonNode result = mapper.readTree(output.toString());
            if (result == null || !result.isObject()) throw new AiUnavailable("AI 응답 형식을 확인할 수 없습니다. 수동 조건을 이용하세요.");
            if (cache.size() >= 100) cache.remove(cache.keySet().iterator().next());
            cache.put(cacheKey, result.deepCopy());
            return result;
        } catch (AiUnavailable e) { throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) throw new AiUnavailable("Gemini 호출이 제한되었어요. 잠시 후 다시 요청하거나 직접 설정으로 비교해 주세요.");
            throw new AiUnavailable("AI 요청을 처리하지 못했어요 (HTTP " + e.getStatusCode().value() + "). 키·모델 설정을 확인하거나 직접 설정을 이용해 주세요.");
        } catch (Exception ignored) {
            // 원문 예외에는 제공자 응답 등이 포함될 수 있으므로 키·본문·계좌정보를 로그로 남기지 않습니다.
            throw new AiUnavailable("AI 연결이 실패했거나 호출이 제한되었습니다. 잠시 후 다시 요청하세요. 계산과 수동 입력은 사용 가능합니다.");
        }
    }
    private BigDecimal cost(long input, long output) { return inputPrice.multiply(BigDecimal.valueOf(input)).add(outputPrice.multiply(BigDecimal.valueOf(output))).movePointLeft(6); }
    private void persist() {
        try {
            Files.createDirectories(ledger.getParent());
            Path temp = ledger.resolveSibling("ai-usage.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), status());
            Files.move(temp, ledger, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { ledgerAvailable = false; throw new AiUnavailable("사용량을 저장하지 못해 AI 호출을 중지했습니다. 계산은 계속 가능합니다."); }
    }
    public static class AiUnavailable extends RuntimeException { public AiUnavailable(String message) { super(message); } }
}
