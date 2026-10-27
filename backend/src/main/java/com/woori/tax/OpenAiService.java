package com.woori.tax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiService {
    private static final BigDecimal MAX_APP_BUDGET = new BigDecimal("20");
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String key, model;
    private final int maxCalls, maxOutput;
    private final BigDecimal budget, inputPrice, outputPrice, searchPrice;
    private final Path ledger = Path.of("data", "ai-usage-openai.json");
    private final Map<String, JsonNode> cache = new LinkedHashMap<>();
    private final Map<String, TimedGrounding> groundingCache = new LinkedHashMap<>();

    public record GroundingSource(String id, String title, String url) {}
    public record GroundingClaim(String text, List<String> sourceIds) {}
    public record GroundedResult(String text, List<GroundingSource> sources, List<GroundingClaim> claims) {}
    private record TimedGrounding(GroundedResult result, Instant at) {}

    private int calls, searchCalls;
    private long inputTokens, outputTokens;
    private BigDecimal estimatedCost = BigDecimal.ZERO;
    private boolean ledgerAvailable = true;

    public OpenAiService(ObjectMapper mapper, @Value("${app.openai.key:}") String key,
                         @Value("${app.openai.model:gpt-6.1-sol}") String model,
                         @Value("${app.openai.max-calls:40}") int maxCalls,
                         @Value("${app.openai.budget-usd:20}") BigDecimal budget,
                         @Value("${app.openai.input-per-million:2.00}") BigDecimal inputPrice,
                         @Value("${app.openai.output-per-million:10.00}") BigDecimal outputPrice,
                         @Value("${app.openai.web-search-per-call:0.01}") BigDecimal searchPrice,
                         @Value("${app.openai.max-output-tokens:3000}") int maxOutput) {
        this.mapper = mapper; this.key = key; this.model = model; this.maxCalls = maxCalls;
        this.budget = budget; this.inputPrice = inputPrice; this.outputPrice = outputPrice;
        this.searchPrice = searchPrice; this.maxOutput = maxOutput;
        if (!model.matches("[a-zA-Z0-9.\\-]{1,100}") || maxOutput < 100 || maxOutput > 6000 || maxCalls < 0
            || budget.signum() < 0 || inputPrice.signum() <= 0 || outputPrice.signum() <= 0 || searchPrice.signum() < 0)
            throw new IllegalArgumentException("OpenAI 설정값을 확인하세요.");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        client = RestClient.builder().requestFactory(factory).baseUrl("https://api.openai.com").build();
        if (Files.exists(ledger)) {
            try {
                JsonNode saved = mapper.readTree(ledger.toFile());
                if (!saved.path("calls").isIntegralNumber() || !saved.path("inputTokens").isIntegralNumber()
                    || !saved.path("outputTokens").isIntegralNumber() || !saved.path("estimatedCostUsd").isNumber())
                    throw new IllegalArgumentException("usage fields missing");
                calls = saved.path("calls").intValue(); searchCalls = saved.path("searchCalls").asInt(0);
                inputTokens = saved.path("inputTokens").longValue(); outputTokens = saved.path("outputTokens").longValue();
                estimatedCost = saved.path("estimatedCostUsd").decimalValue();
                if (calls < 0 || searchCalls < 0 || inputTokens < 0 || outputTokens < 0 || estimatedCost.signum() < 0)
                    ledgerAvailable = false;
            } catch (Exception ignored) { ledgerAvailable = false; }
        }
    }

    public synchronized Map<String, Object> status() {
        return Map.of("configured", !key.isBlank(), "model", model,
            "calls", calls, "searchCalls", searchCalls, "maxCalls", maxCalls,
            "inputTokens", inputTokens, "outputTokens", outputTokens,
            "estimatedCostUsd", estimatedCost, "budgetUsd", budget, "ledgerAvailable", ledgerAvailable);
    }

    public synchronized JsonNode json(String system, String prompt, String version) {
        String cacheKey = model + version + system + prompt;
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey).deepCopy();
        if (prompt.length() + system.length() > 16000) throw new AiUnavailable("AI 입력 길이 한도를 초과했습니다.");
        long reservedInput = (system + prompt).getBytes(StandardCharsets.UTF_8).length + 200;
        BigDecimal reserved = reserve(reservedInput, 0);
        reserveCall(reserved);
        try {
            JsonNode response = send(Map.of("model", model, "instructions", system, "input", prompt,
                "store", false, "max_output_tokens", maxOutput,
                "text", Map.of("format", Map.of("type", "json_object"))));
            account(response, reserved, 0);
            JsonNode result = mapper.readTree(outputText(response));
            if (result == null || !result.isObject()) throw new AiUnavailable("AI 응답 형식을 확인할 수 없습니다.");
            if (cache.size() >= 100) cache.remove(cache.keySet().iterator().next());
            cache.put(cacheKey, result.deepCopy());
            return result;
        } catch (AiUnavailable e) { throw e;
        } catch (RestClientResponseException e) { throw providerFailure(e);
        } catch (ResourceAccessException e) { throw new AiUnavailable(connectionFailureMessage(e));
        } catch (JsonProcessingException e) { throw new AiUnavailable("AI 응답을 JSON으로 읽지 못했습니다.");
        } catch (Exception e) { throw new AiUnavailable("백엔드 AI 처리 오류 (" + e.getClass().getSimpleName() + ")."); }
    }

    public synchronized GroundedResult search(String prompt, String version) {
        String cacheKey = model + version + prompt;
        TimedGrounding cached = groundingCache.get(cacheKey);
        if (cached != null && cached.at().isAfter(Instant.now().minus(Duration.ofMinutes(30)))) return cached.result();
        if (prompt.length() > 12000) throw new AiUnavailable("자료 조회 입력이 너무 깁니다.");
        long reservedInput = prompt.getBytes(StandardCharsets.UTF_8).length + 200;
        BigDecimal reserved = reserve(reservedInput, 4);
        reserveCall(reserved);
        try {
            JsonNode response = send(Map.of("model", model, "input", prompt, "store", false,
                "max_output_tokens", maxOutput, "max_tool_calls", 4,
                "tools", List.of(Map.of("type", "web_search", "external_web_access", true,
                    "search_context_size", "medium")), "tool_choice", "required"));
            int actualSearchCalls = 0;
            for (JsonNode item : response.path("output")) if ("web_search_call".equals(item.path("type").asText())) actualSearchCalls++;
            account(response, reserved, actualSearchCalls);
            GroundedResult result = parseGrounded(response);
            if (groundingCache.size() >= 20) groundingCache.remove(groundingCache.keySet().iterator().next());
            groundingCache.put(cacheKey, new TimedGrounding(result, Instant.now()));
            return result;
        } catch (AiUnavailable e) { throw e;
        } catch (RestClientResponseException e) { throw providerFailure(e);
        } catch (ResourceAccessException e) { throw new AiUnavailable(connectionFailureMessage(e));
        } catch (Exception e) { throw new AiUnavailable("최신 자료 검색 처리 오류 (" + e.getClass().getSimpleName() + ")."); }
    }

    private JsonNode send(Map<String, Object> body) {
        JsonNode response = client.post().uri("/v1/responses").header("Authorization", "Bearer " + key)
            .body(body).retrieve().body(JsonNode.class);
        if (response == null || !"completed".equals(response.path("status").asText()))
            throw new AiUnavailable("OpenAI 응답이 완성되지 않았습니다.");
        return response;
    }

    private void reserveCall(BigDecimal reserved) {
        if (key.isBlank()) throw new AiUnavailable("OpenAI 키가 설정되지 않았습니다. 수동 조건 입력과 계산을 사용할 수 있습니다.");
        if (!ledgerAvailable) throw new AiUnavailable("사용량 기록 파일을 확인해야 합니다.");
        if (calls >= maxCalls) throw new AiUnavailable("앱의 AI 호출 한도에 도달했습니다.");
        if (estimatedCost.add(reserved).compareTo(budget.min(MAX_APP_BUDGET)) > 0)
            throw new AiUnavailable("앱의 AI 예산 한도에 도달했습니다.");
        calls++; estimatedCost = estimatedCost.add(reserved); persist();
    }

    private BigDecimal reserve(long input, int webCalls) { return cost(input, maxOutput).add(searchPrice.multiply(BigDecimal.valueOf(webCalls))); }
    private BigDecimal cost(long input, long output) {
        return inputPrice.multiply(BigDecimal.valueOf(input)).add(outputPrice.multiply(BigDecimal.valueOf(output))).movePointLeft(6);
    }
    private void account(JsonNode response, BigDecimal reserved, int webCalls) {
        JsonNode usage = response.path("usage");
        if (!usage.path("input_tokens").isIntegralNumber() || !usage.path("output_tokens").isIntegralNumber())
            throw new AiUnavailable("OpenAI 사용량을 확인할 수 없습니다.");
        long in = usage.path("input_tokens").longValue(), out = usage.path("output_tokens").longValue();
        if (in < 0 || out < 0) throw new AiUnavailable("OpenAI 사용량을 확인할 수 없습니다.");
        inputTokens += in; outputTokens += out; searchCalls += webCalls;
        estimatedCost = estimatedCost.subtract(reserved).add(cost(in, out)).add(searchPrice.multiply(BigDecimal.valueOf(webCalls)));
        persist();
    }

    static String outputText(JsonNode response) {
        StringBuilder output = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content"))
                if ("output_text".equals(content.path("type").asText())) output.append(content.path("text").asText());
        }
        if (output.isEmpty()) throw new AiUnavailable("OpenAI 응답에 텍스트가 없습니다.");
        return output.toString();
    }

    static GroundedResult parseGrounded(JsonNode response) {
        boolean searched = false;
        for (JsonNode item : response.path("output")) if ("web_search_call".equals(item.path("type").asText())) searched = true;
        if (!searched) throw new AiUnavailable("실제 웹 검색이 수행되지 않았습니다.");
        StringBuilder allText = new StringBuilder();
        Map<String, GroundingSource> sources = new LinkedHashMap<>();
        List<GroundingClaim> claims = new ArrayList<>();
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) continue;
                String text = content.path("text").asText("");
                allText.append(text);
                for (JsonNode annotation : content.path("annotations")) {
                    if (!"url_citation".equals(annotation.path("type").asText())) continue;
                    String url = annotation.path("url").asText("");
                    if (!url.startsWith("https://") || sources.size() >= 8 && !sources.containsKey(url)) continue;
                    sources.computeIfAbsent(url, ignored -> new GroundingSource("live-" + sources.size(),
                        annotation.path("title").asText("검색 자료"), url));
                    String claim = citedSentence(text, annotation.path("start_index").asInt(text.length()));
                    if (!claim.isBlank() && claims.size() < 12)
                        claims.add(new GroundingClaim(claim, List.of(sources.get(url).id())));
                }
            }
        }
        if (sources.isEmpty() || claims.isEmpty()) throw new AiUnavailable("출처가 연결된 검색 결과를 얻지 못했습니다.");
        return new GroundedResult(allText.substring(0, Math.min(allText.length(), 6000)),
            List.copyOf(sources.values()), List.copyOf(claims));
    }

    private static String citedSentence(String text, int citationStart) {
        int end = Math.max(0, Math.min(citationStart, text.length()));
        while (end > 0 && (Character.isWhitespace(text.charAt(end - 1)) || ".!?".indexOf(text.charAt(end - 1)) >= 0)) end--;
        int start = Math.max(0, end - 350);
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '.' || c == '!' || c == '?') { start = i + 1; break; }
        }
        return text.substring(start, end).replaceAll("\\s+", " ").trim();
    }

    private AiUnavailable providerFailure(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401) return new AiUnavailable("OpenAI 키가 유효하지 않습니다. 새 키 설정을 확인해 주세요.");
        if (status == 404) return new AiUnavailable("설정된 OpenAI 모델(" + model + ")을 사용할 수 없습니다.");
        if (status == 429) return new AiUnavailable("OpenAI 잔액 또는 호출 한도를 확인해 주세요.");
        return new AiUnavailable("OpenAI 요청에 실패했습니다 (HTTP " + status + ").");
    }

    static String connectionFailureMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException)
                return "OpenAI 응답 시간이 초과되었습니다. 잠시 후 다시 요청하세요.";
            if (cause instanceof ConnectException || cause instanceof UnknownHostException)
                return "OpenAI 서버에 연결할 수 없습니다. 백엔드의 인터넷 연결을 확인해 주세요.";
        }
        return "OpenAI 네트워크 요청에 실패했습니다. 백엔드의 인터넷 연결을 확인해 주세요.";
    }

    private void persist() {
        try {
            Files.createDirectories(ledger.getParent());
            Path temp = ledger.resolveSibling("ai-usage-openai.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), status());
            Files.move(temp, ledger, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            ledgerAvailable = false;
            throw new AiUnavailable("사용량을 저장하지 못해 AI 호출을 중지했습니다. 계산은 계속 가능합니다.");
        }
    }

    public static class AiUnavailable extends RuntimeException {
        public AiUnavailable(String message) { super(message); }
    }
}
