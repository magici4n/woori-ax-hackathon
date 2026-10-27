package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OpenAiServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void readsMessageAfterReasoningOutput() throws Exception {
        var response = mapper.readTree("""
            {"output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"{\\"ok\\":true}"}]}]}
            """);
        assertThat(OpenAiService.outputText(response)).isEqualTo("{\"ok\":true}");
    }

    @Test void webResearchRequiresRealSearchAndClickableCitation() throws Exception {
        String citedText = "공식 발표에 따르면 다음 달 실적 발표가 예정되어 있습니다. [1]";
        var response = mapper.readTree("""
            {"output":[
              {"type":"web_search_call","action":{"type":"search"}},
              {"type":"message","content":[{"type":"output_text","text":"공식 발표에 따르면 다음 달 실적 발표가 예정되어 있습니다. [1]",
                "annotations":[{"type":"url_citation","start_index":%d,"end_index":%d,"title":"Investor Relations","url":"https://example.com/ir"}]}]}
            ]}
            """.formatted(citedText.indexOf("[1]"), citedText.length()));
        var result = OpenAiService.parseGrounded(response);
        assertThat(result.sources()).extracting(OpenAiService.GroundingSource::url).containsExactly("https://example.com/ir");
        assertThat(result.claims()).extracting(OpenAiService.GroundingClaim::sourceIds).containsExactly(List.of("live-0"));
        assertThat(result.claims().get(0).text()).contains("실적 발표");
        var noSearch = mapper.readTree("""
            {"output":[{"type":"message","content":[{"type":"output_text","text":"근거", "annotations":[{"type":"url_citation","url":"https://example.com"}]}]}]}
            """);
        assertThatThrownBy(() -> OpenAiService.parseGrounded(noSearch)).isInstanceOf(OpenAiService.AiUnavailable.class);
    }

    @Test void timeoutIsReportedWithoutLeakingProviderDetails() {
        var error = new ResourceAccessException("request with secret data", new HttpTimeoutException("request timed out"));
        assertThat(OpenAiService.connectionFailureMessage(error)).contains("응답 시간이 초과")
            .doesNotContain("secret data");
    }
}
