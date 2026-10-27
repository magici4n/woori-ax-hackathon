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

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpenAiService {
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String key, model;
    private final int maxOutput;
    private final Map<String, JsonNode> cache = new LinkedHashMap<>();
    private final Map<String, TimedGrounding> groundingCache = new ConcurrentHashMap<>();

    public record GroundingSource(String id, String title, String url) {}
    public record GroundingClaim(String text, List<String> sourceIds) {}
    public record GroundedResult(String text, List<GroundingSource> sources, List<GroundingClaim> claims) {}
    private record TimedGrounding(GroundedResult result, Instant at) {}

    public OpenAiService(ObjectMapper mapper, @Value("${app.openai.key:}") String key,
                         @Value("${app.openai.model:gpt-6.1-sol}") String model,
                         @Value("${app.openai.max-output-tokens:3000}") int maxOutput) {
        this.mapper = mapper; this.key = key; this.model = model; this.maxOutput = maxOutput;
        if (!model.matches("[a-zA-Z0-9.\\-]{1,100}") || maxOutput < 100 || maxOutput > 6000)
            throw new IllegalArgumentException("OpenAI 설정값을 확인하세요.");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        client = RestClient.builder().requestFactory(factory).baseUrl("https://api.openai.com").build();
    }

    public synchronized JsonNode json(String system, String prompt, String version) {
        return jsonFormat(system, prompt, version, Map.of("type", "json_object"));
    }

    public synchronized JsonNode jsonSchema(String system, String prompt, String version, Map<String, Object> schema) {
        return jsonFormat(system, prompt, version,
            Map.of("type", "json_schema", "name", "research_articles", "strict", true, "schema", schema));
    }

    private JsonNode jsonFormat(String system, String prompt, String version, Map<String, Object> format) {
        String cacheKey = model + version + system + prompt + format;
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey).deepCopy();
        if (prompt.length() + system.length() > 16000) throw new AiUnavailable("AI 입력 길이 한도를 초과했습니다.");
        requireKey();
        try {
            JsonNode response = send(Map.of("model", model, "instructions", system, "input", prompt,
                "store", false, "max_output_tokens", maxOutput,
                "text", Map.of("format", format)));
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

    public GroundedResult search(String prompt, String version) {
        return search(prompt, version, List.of());
    }

    public GroundedResult search(String prompt, String version, List<String> allowedDomains) {
        String cacheKey = model + version + prompt + allowedDomains;
        TimedGrounding cached = groundingCache.get(cacheKey);
        if (cached != null && cached.at().isAfter(Instant.now().minus(Duration.ofMinutes(30)))) return cached.result();
        if (prompt.length() > 12000) throw new AiUnavailable("자료 조회 입력이 너무 깁니다.");
        requireKey();
        try {
            Map<String, Object> webTool = new LinkedHashMap<>();
            webTool.put("type", "web_search");
            webTool.put("external_web_access", true);
            webTool.put("search_context_size", "medium");
            if (!allowedDomains.isEmpty()) webTool.put("filters", Map.of("allowed_domains", allowedDomains));
            JsonNode response = send(Map.of("model", model, "input", prompt, "store", false,
                "max_output_tokens", maxOutput, "max_tool_calls", 2,
                "reasoning", Map.of("effort", "low"),
                "tools", List.of(webTool), "tool_choice", "required",
                "include", List.of("web_search_call.action.sources")));
            GroundedResult result = parseGrounded(response);
            if (groundingCache.size() >= 20) {
                var keys = groundingCache.keySet().iterator();
                if (keys.hasNext()) groundingCache.remove(keys.next());
            }
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
        if (response == null) throw new AiUnavailable("OpenAI 응답이 비어 있습니다.");
        if (!"completed".equals(response.path("status").asText())) {
            String reason = response.path("incomplete_details").path("reason").asText();
            if ("max_output_tokens".equals(reason))
                throw new AiUnavailable("AI 응답이 출력 토큰 한도에 도달해 분석을 완료하지 못했습니다.");
            throw new AiUnavailable("OpenAI 응답이 완성되지 않았습니다.");
        }
        return response;
    }

    private void requireKey() {
        if (key.isBlank()) throw new AiUnavailable("OpenAI 키가 설정되지 않았습니다. 수동 조건 입력과 계산을 사용할 수 있습니다.");
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
        Map<String, String> consulted = new LinkedHashMap<>();
        for (JsonNode item : response.path("output")) {
            if (!"web_search_call".equals(item.path("type").asText())) continue;
            searched = true;
            for (JsonNode source : item.path("action").path("sources")) {
                String url = source.path("url").asText("");
                if (url.startsWith("https://")) consulted.putIfAbsent(url, source.path("title").asText(""));
            }
        }
        if (!searched) throw new AiUnavailable("실제 웹 검색이 수행되지 않았습니다.");
        StringBuilder allText = new StringBuilder();
        Map<String, GroundingSource> sources = new LinkedHashMap<>();
        List<GroundingClaim> claims = new ArrayList<>();
        int citationCount = 0;
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) continue;
                String text = content.path("text").asText("");
                allText.append(text);
                List<JsonNode> citations = new ArrayList<>();
                for (JsonNode annotation : content.path("annotations"))
                    if ("url_citation".equals(annotation.path("type").asText())) citations.add(annotation);
                citations.sort(java.util.Comparator.comparingInt(annotation -> annotation.path("start_index").asInt(text.length())));
                int previousCitationEnd = 0;
                String precedingClaim = "";
                for (JsonNode annotation : citations) {
                    if (!"url_citation".equals(annotation.path("type").asText())) continue;
                    citationCount++;
                    String url = annotation.path("url").asText("");
                    String sourceUrl = canonicalSourceUrl(url);
                    if (!sourceUrl.startsWith("https://") || sources.size() >= 48 && !sources.containsKey(sourceUrl)) continue;
                    GroundingSource previous = sources.get(sourceUrl);
                    String title = annotation.path("title").asText("");
                    if (title.isBlank()) title = consulted.getOrDefault(url, "검색 자료");
                    if (previous == null) sources.put(sourceUrl, new GroundingSource("live-" + sources.size(), title, sourceUrl));
                    else if (previous.title().isBlank() || "검색 자료".equals(previous.title()))
                        sources.put(sourceUrl, new GroundingSource(previous.id(), title, sourceUrl));
                    int citationStart = annotation.path("start_index").asInt(text.length());
                    String claim = citedPassage(text, previousCitationEnd, citationStart);
                    if (claim.isBlank() && citationStart - previousCitationEnd <= 20) claim = precedingClaim;
                    if (!claim.isBlank() && claims.size() < 32)
                        claims.add(new GroundingClaim(claim, List.of(sources.get(sourceUrl).id())));
                    if (!claim.isBlank()) precedingClaim = claim;
                    previousCitationEnd = Math.max(previousCitationEnd,
                        annotation.path("end_index").asInt(citationStart));
                }
                // 검색에 실제 사용한 URL이 본문에도 명시된 경우에만 인용 누락을 보완합니다.
                for (var consultedSource : consulted.entrySet()) {
                    String url = consultedSource.getKey();
                    String sourceUrl = canonicalSourceUrl(url);
                    if (sources.containsKey(sourceUrl) || sources.size() >= 48) continue;
                    int position = text.indexOf(url);
                    if (position < 0) continue;
                    int paragraphStart = text.lastIndexOf("\n\n", position);
                    int blockStart = paragraphStart < 0 ? 0 : paragraphStart + 2;
                    String articleBlock = text.substring(blockStart, position);
                    if (consulted.keySet().stream().anyMatch(other -> !other.equals(url) && articleBlock.contains(other))) continue;
                    String claim = citedPassage(text, blockStart, position);
                    if (claim.length() < 20) continue;
                    String title = consultedSource.getValue().isBlank()
                        ? claim.substring(0, Math.min(claim.length(), 100)) : consultedSource.getValue();
                    GroundingSource source = new GroundingSource("live-" + sources.size(), title, sourceUrl);
                    sources.put(sourceUrl, source);
                    if (claims.size() < 32) claims.add(new GroundingClaim(claim, List.of(source.id())));
                }
            }
        }
        if (claims.isEmpty()) {
            if (consulted.isEmpty() && citationCount == 0)
                throw new AiUnavailable("웹 검색은 실행됐지만 조회한 원문 URL과 본문 인용이 모두 없습니다. 검색 범위와 모델 응답을 확인해 주세요.");
            if (citationCount == 0)
                throw new AiUnavailable("웹 검색 URL " + consulted.size() + "건은 조회됐지만 응답에 출처 인용이나 URL이 연결된 사실 문장이 없습니다.");
            throw new AiUnavailable("본문 인용 " + citationCount + "건은 있으나 분석에 사용할 사실 문장을 추출하지 못했습니다.");
        }
        return new GroundedResult(allText.substring(0, Math.min(allText.length(), 6000)),
            List.copyOf(sources.values()), List.copyOf(claims));
    }

    private static String citedPassage(String text, int previousCitationEnd, int citationStart) {
        int end = Math.max(0, Math.min(citationStart, text.length()));
        int start = Math.max(Math.max(0, Math.min(previousCitationEnd, end)), end - 800);
        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph >= start) start = paragraph + 2;
        return text.substring(start, end).replaceAll("https?://\\S+", " ")
            .replaceAll("\\s+", " ").replaceAll("^[*#|:;\\-\\s]+", "").trim();
    }

    private static String canonicalSourceUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawAuthority() == null) return url;
            List<String> query = new ArrayList<>();
            if (uri.getRawQuery() != null) for (String part : uri.getRawQuery().split("&"))
                if (!part.toLowerCase(java.util.Locale.ROOT).startsWith("utm_")
                    && !part.toLowerCase(java.util.Locale.ROOT).startsWith("fbclid=")) query.add(part);
            return "https://" + uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT)
                + (uri.getRawPath() == null ? "" : uri.getRawPath())
                + (query.isEmpty() ? "" : "?" + String.join("&", query));
        } catch (IllegalArgumentException ignored) { return url; }
    }

    private AiUnavailable providerFailure(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401) return new AiUnavailable("OpenAI 키가 유효하지 않습니다. 새 키 설정을 확인해 주세요.");
        if (status == 404) return new AiUnavailable("설정된 OpenAI 모델(" + model + ")을 사용할 수 없습니다.");
        if (status == 429) return new AiUnavailable("OpenAI 잔액 또는 호출 한도를 확인해 주세요.");
        if (status >= 500) return new AiUnavailable("OpenAI 응답 오류 (HTTP " + status + "). 검색 또는 분석 요청을 완료하지 못했습니다.");
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

    public static class AiUnavailable extends RuntimeException {
        public AiUnavailable(String message) { super(message); }
    }
}
