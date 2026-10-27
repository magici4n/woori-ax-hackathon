package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void chatAndProposalRequestsUseStrictStructuredOutputs() throws Exception {
        for (var schema : List.of(AiSchemas.CONDITION_EXTRACTION, AiSchemas.PORTFOLIO_PROPOSALS)) {
            assertStrictObjects(mapper.valueToTree(schema));
            var builder = RestClient.builder().baseUrl("https://api.openai.com");
            var server = MockRestServiceServer.bindTo(builder).build();
            var service = new OpenAiService(mapper, "test-key", "gpt-6.1-sol", 5000, builder.build());
            var expected = Map.of("model", "gpt-6.1-sol", "instructions", "Return JSON.", "input", "{}",
                "store", false, "max_output_tokens", 5000, "reasoning", Map.of("effort", "low"),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "structured_response", "strict", true, "schema", schema)));
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(mapper.writeValueAsString(expected)))
                .andRespond(withSuccess("""
                    {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\\"ok\\":true}"}]}]}
                    """, MediaType.APPLICATION_JSON));
            assertThat(service.jsonSchema("Return JSON.", "{}", "contract", schema).path("ok").asBoolean()).isTrue();
            server.verify();
        }
    }

    @Test void badRequestReportsRejectedParameterWithoutProviderMessage() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new OpenAiService(mapper, "test-key", "gpt-6.1-sol", 5000, builder.build());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                {"error":{"message":"raw provider request with secret data","code":"unsupported_value","param":"text.format"}}
                """));
        assertThatThrownBy(() -> service.jsonSchema("Return JSON.", "{}", "error", AiSchemas.CONDITION_EXTRACTION))
            .isInstanceOf(OpenAiService.AiUnavailable.class).hasMessageContaining("HTTP 400")
            .hasMessageContaining("text.format").hasMessageContaining("unsupported_value")
            .hasMessageNotContaining("secret data").hasMessageNotContaining("test-key");
        server.verify();
    }

    private void assertStrictObjects(com.fasterxml.jackson.databind.JsonNode schema) {
        if ("object".equals(schema.path("type").asText())) {
            assertThat(schema.path("additionalProperties").isBoolean()).isTrue();
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            var fields = new HashSet<String>();
            schema.path("properties").fieldNames().forEachRemaining(fields::add);
            var required = new HashSet<String>();
            schema.path("required").forEach(field -> required.add(field.asText()));
            assertThat(required).isEqualTo(fields);
            schema.path("properties").forEach(this::assertStrictObjects);
        }
        if (schema.has("items")) assertStrictObjects(schema.path("items"));
        schema.path("anyOf").forEach(this::assertStrictObjects);
    }

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

    @Test void consultedUrlWithoutArticleTitleDoesNotDisplaceCitedArticle() throws Exception {
        var response = mapper.readTree("""
            {"output":[
              {"type":"web_search_call","action":{"type":"search","sources":[
                {"type":"url","url":"https://www.yna.co.kr/view/uncited"}]}},
              {"type":"message","content":[{"type":"output_text","text":"애플 실적 발표 [1]",
                "annotations":[{"type":"url_citation","start_index":9,"title":"애플 실적 발표",
                  "url":"https://www.yna.co.kr/view/cited"}]}]}
            ]}
            """);
        var result = OpenAiService.parseGrounded(response);
        assertThat(result.sources()).extracting(OpenAiService.GroundingSource::url)
            .containsExactly("https://www.yna.co.kr/view/cited");
        assertThat(result.claims()).hasSize(1);
    }

    @Test void citationAfterSeparateUrlLineKeepsArticleFacts() throws Exception {
        String article = "애플은 분기 실적에서 매출 증가를 발표했고 서비스 사업의 성장도 설명했습니다.\nhttps://www.yna.co.kr/view/apple-results ";
        var response = mapper.readTree("""
            {"output":[{"type":"web_search_call","action":{"type":"search"}},
              {"type":"message","content":[{"type":"output_text","text":%s,
                "annotations":[{"type":"url_citation","start_index":%d,"title":"애플 분기 실적",
                  "url":"https://www.yna.co.kr/view/apple-results"}]}]}]}
            """.formatted(mapper.writeValueAsString(article + "[1]"), article.length()));
        var result = OpenAiService.parseGrounded(response);
        assertThat(result.claims()).hasSize(1);
        assertThat(result.claims().get(0).text()).contains("매출 증가", "서비스 사업");
        assertThat(result.claims().get(0).text()).doesNotContain("https://");
    }

    @Test void literalConsultedUrlCanRecoverAClaimWithoutAnnotation() throws Exception {
        var response = mapper.readTree("""
            {"output":[{"type":"web_search_call","action":{"type":"search","sources":[
              {"type":"url","url":"https://www.yna.co.kr/view/apple-results"}]}},
              {"type":"message","content":[{"type":"output_text",
                "text":"애플 분기 실적: 회사는 매출 증가와 서비스 사업의 성장을 발표했습니다.\\nhttps://www.yna.co.kr/view/apple-results"}]}]}
            ]}
            """);
        var result = OpenAiService.parseGrounded(response);
        assertThat(result.sources()).extracting(OpenAiService.GroundingSource::url)
            .containsExactly("https://www.yna.co.kr/view/apple-results");
        assertThat(result.claims()).extracting(OpenAiService.GroundingClaim::text)
            .containsExactly("애플 분기 실적: 회사는 매출 증가와 서비스 사업의 성장을 발표했습니다.");
    }

    @Test void consultedUrlAloneIsNotTreatedAsVerifiedArticle() throws Exception {
        var response = mapper.readTree("""
            {"output":[{"type":"web_search_call","action":{"type":"search","sources":[
              {"type":"url","url":"https://www.yna.co.kr/view/apple-results"}]}},
              {"type":"message","content":[{"type":"output_text","text":"검색 결과가 확인되지 않았습니다."}]}]}
            """);
        assertThatThrownBy(() -> OpenAiService.parseGrounded(response))
            .isInstanceOf(OpenAiService.AiUnavailable.class)
            .hasMessageContaining("URL 1건");
    }

    @Test void trackingParametersDoNotDuplicateCitedAndConsultedArticle() throws Exception {
        String text = "애플은 새 제품의 성능 향상을 발표했습니다. "
            + "([뉴스](https://www.newsis.com/view/NISX123?utm_source=openai)) "
            + "원문 URL: https://www.newsis.com/view/NISX123";
        int citationStart = text.indexOf("([뉴스]");
        var response = mapper.readTree("""
            {"output":[{"type":"web_search_call","action":{"type":"search","sources":[
              {"type":"url","url":"https://www.newsis.com/view/NISX123"}]}},
              {"type":"message","content":[{"type":"output_text","text":%s,
                "annotations":[{"type":"url_citation","start_index":%d,"end_index":%d,
                  "title":"애플 신제품", "url":"https://www.newsis.com/view/NISX123?utm_source=openai"}]}]}]}
            """.formatted(mapper.writeValueAsString(text), citationStart,
                text.indexOf("))", citationStart) + 2));
        var result = OpenAiService.parseGrounded(response);
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).url()).isEqualTo("https://www.newsis.com/view/NISX123");
        assertThat(result.claims()).hasSize(1);
    }
}
