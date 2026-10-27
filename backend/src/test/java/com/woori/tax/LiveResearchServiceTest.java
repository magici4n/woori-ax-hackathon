package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class LiveResearchServiceTest {
    @Test void researchIsReusedByLaterConsumersWithoutAnotherPaidSearch() throws Exception {
        OpenAiService ai = mock(OpenAiService.class);
        var found = new OpenAiService.GroundedResult("확인된 사실",
            List.of(new OpenAiService.GroundingSource("live-0", "Tesla quarterly results", "https://ir.tesla.com/tesla-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("확인된 사실", List.of("live-0"))));
        when(ai.search(anyString(), anyString())).thenReturn(found);
        when(ai.search(anyString(), anyString(), anyList())).thenReturn(found);
        var service = new LiveResearchService(ai, new DemoData(new ObjectMapper()));
        assertThat(service.current()).isNull();
        var curated = service.research();
        assertThat(curated.sources()).hasSize(1);
        assertThat(service.current()).isSameAs(curated);
        assertThat(service.research()).isSameAs(curated);
        verify(ai, atLeastOnce()).search(anyString(), anyString(), argThat(domains ->
            domains.contains("yna.co.kr") && domains.contains("hankyung.com") && !domains.contains("reuters.com")));
        verify(ai, atLeastOnce()).search(anyString(), anyString(), argThat(domains ->
            domains.contains("sec.gov") && domains.contains("tesla.com")));
    }
    @Test void citedArticlesBecomePerStockSummariesAndReuseTheSameResponses() throws Exception {
        OpenAiService ai = mock(OpenAiService.class);
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("테슬라가 실적을 발표했습니다.",
            List.of(new OpenAiService.GroundingSource("live-0", "Tesla quarterly results", "https://ir.tesla.com/tesla-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("테슬라가 실적을 발표했습니다.", List.of("live-0"))));
        when(ai.search(anyString(), anyString())).thenReturn(found);
        when(ai.search(anyString(), anyString(), anyList())).thenReturn(found);
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenReturn(mapper.readTree("""
            {"articles":[{"symbol":"TSLA","sourceId":"live-0","kind":"company","publishedAt":"2026-10-20",
              "summary":"**실적을 발표했습니다.**","growth":"매출 증가","risk":"비용 증가","relevance":"보유 판단 참고",
              "eventDate":"","eventStatus":"unverified"}],
             "stockAnalyses":[{"symbol":"TSLA","summary":"실적 발표와 비용 증가를 함께 확인했습니다.",
               "growth":"매출 증가가 지속되면 성장에 기여합니다.","risk":"비용 증가가 수익성을 압박합니다.",
               "decisionPoint":"보유하면 실적 변화에 계속 노출됩니다.","nextCheck":"다음 실적의 비용 추이",
               "keywords":["실적","비용"],"sourceIds":["live-0"]}]}
            """));
        var service = new LiveResearchService(ai, new DemoData(mapper), mapper);
        var first = service.brief();
        assertThat(first.mode()).isEqualTo("structured");
        assertThat(first.articles()).hasSize(1);
        assertThat(first.articles().get(0).title()).isEqualTo("Tesla quarterly results");
        assertThat(first.articles().get(0).summary()).isEqualTo("실적을 발표했습니다.");
        assertThat(first.articles().get(0).growth()).isEqualTo("매출 증가");
        assertThat(first.stockAnalyses()).hasSize(1);
        assertThat(first.stockAnalyses().get(0).sourceIds()).containsExactly("live-0");
        assertThat(service.brief()).isSameAs(first);
        assertThat(service.brief(true)).isNotSameAs(first);
        verify(ai, atLeastOnce()).search(anyString(), anyString(), anyList());
        verify(ai, times(2)).jsonSchema(anyString(), anyString(), anyString(), anyMap());
    }
    @Test void explicitRefreshRepeatsSearchAndShowsProviderFailures() throws Exception {
        OpenAiService ai = mock(OpenAiService.class);
        ObjectMapper mapper = new ObjectMapper();
        var official = new OpenAiService.GroundedResult("Apple quarterly results",
            List.of(new OpenAiService.GroundingSource("live-0", "Apple quarterly results", "https://www.apple.com/newsroom/apple-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("Apple quarterly results", List.of("live-0"))));
        when(ai.search(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            String version = invocation.getArgument(1);
            if (version.startsWith("korean-pair"))
                throw new OpenAiService.AiUnavailable("OpenAI 응답 오류 (HTTP 500). 검색 또는 분석 요청을 완료하지 못했습니다.");
            return official;
        });
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap()))
            .thenReturn(mapper.readTree("{\"articles\":[],\"stockAnalyses\":[]}"));
        var service = new LiveResearchService(ai, new DemoData(mapper), mapper);
        var first = service.brief();
        assertThat(first.warnings()).anySatisfy(warning ->
            assertThat(warning).contains("한국 기사 검색(AAPL, MSFT)", "HTTP 500"));
        assertThat(first.warnings()).anySatisfy(warning ->
            assertThat(warning).contains("기사 AI 분석(AAPL)"));
        assertThat(service.brief()).isSameAs(first);
        var refreshed = service.brief(false, true);
        assertThat(refreshed).isNotSameAs(first);
        verify(ai, atLeastOnce()).search(anyString(), argThat(version ->
            version.contains("-refresh-1")), anyList());
    }
    @Test void inventedSourceDoesNotProduceAClaimedSummary() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("검색",
            List.of(new OpenAiService.GroundingSource("live-0", "Tesla IR", "https://ir.tesla.com/tesla-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("검색", List.of("live-0"))));
        var invented = mapper.readTree("""
            {"articles":[{"symbol":"TSLA","sourceId":"invented","summary":"근거 없는 요약"}]}
            """);
        assertThat(LiveResearchService.parseArticles(invented, found, List.of("TSLA"), LocalDate.now())).isEmpty();
    }
    @Test void longButGroundedSummaryDoesNotDiscardTheArticle() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("애플 실적 발표",
            List.of(new OpenAiService.GroundingSource("apple", "애플 분기 실적", "https://www.yna.co.kr/view/AKR20261027000100009")),
            List.of(new OpenAiService.GroundingClaim("애플 분기 실적 발표", List.of("apple"))));
        String longSummary = "애플 분기 실적을 확인했습니다. ".repeat(35);
        var raw = mapper.readTree("{\"articles\":[{\"symbol\":\"AAPL\",\"sourceId\":\"apple\",\"summary\":"
            + mapper.writeValueAsString(longSummary) + "}]}");
        var articles = LiveResearchService.parseArticles(raw, found, List.of("AAPL"), LocalDate.of(2026, 10, 27));
        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).summary().length()).isLessThanOrEqualTo(500);
    }
    @Test void pastArticleDateIsNotDisplayedAsUpcomingEvent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("테슬라 실적", List.of(
            new OpenAiService.GroundingSource("live-0", "테슬라 분기 실적", "https://www.yna.co.kr/view/AKR20261027000100009")),
            List.of(new OpenAiService.GroundingClaim("테슬라 실적 발표", List.of("live-0"))));
        var raw = mapper.readTree("""
            {"articles":[{"symbol":"TSLA","sourceId":"live-0","summary":"분기 실적을 발표했습니다.",
              "eventDate":"2026-01-01","eventStatus":"confirmed"}]}
            """);
        var articles = LiveResearchService.parseArticles(raw, found, List.of("TSLA"), LocalDate.of(2026, 10, 27));
        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).eventDate()).isEmpty();
        assertThat(articles.get(0).eventStatus()).isEqualTo("unverified");
    }
    @Test void failedSummarizationKeepsSourceLinksWithoutMakingUpFacts() throws Exception {
        OpenAiService ai = mock(OpenAiService.class);
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("확인 필요",
            List.of(new OpenAiService.GroundingSource("live-0", "Tesla quarterly results", "https://ir.tesla.com/tesla-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("확인 필요", List.of("live-0"))));
        when(ai.search(anyString(), anyString())).thenReturn(found);
        when(ai.search(anyString(), anyString(), anyList())).thenReturn(found);
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap())).thenThrow(new OpenAiService.AiUnavailable("quota"));
        var brief = new LiveResearchService(ai, new DemoData(mapper), mapper).brief();
        assertThat(brief.mode()).isEqualTo("links_only");
        assertThat(brief.articles()).hasSize(1);
        assertThat(brief.articles().get(0).summary()).isEmpty();
        assertThat(brief.articles().get(0).url()).isEqualTo("https://ir.tesla.com/tesla-quarterly-results");
    }

    @Test void koreanReportsTakePriorityAndOfficialReleaseIsFallback() {
        var found = new OpenAiService.GroundedResult("검색된 사실", List.of(
            new OpenAiService.GroundingSource("official-results", "Tesla quarterly results", "https://ir.tesla.com/tesla-quarterly-results"),
            new OpenAiService.GroundingSource("official-product", "Tesla product update", "https://ir.tesla.com/tesla-product-update"),
            new OpenAiService.GroundingSource("reuters", "Tesla earnings outlook", "https://www.reuters.com/business/autos-transportation/tesla-earnings-outlook"),
            new OpenAiService.GroundingSource("yonhap", "테슬라 실적 발표", "https://www.yna.co.kr/view/AKR20261027000100009"),
            new OpenAiService.GroundingSource("news1", "테슬라 투자 전망", "https://www.news1.kr/articles/tesla-investment"),
            new OpenAiService.GroundingSource("blog", "Tesla stock tip", "https://personal-blog.example/tesla-stock-tip"),
            new OpenAiService.GroundingSource("nike", "Nike quarterly results", "https://investors.nike.com/nike-quarterly-results")),
            List.of(
                new OpenAiService.GroundingClaim("Tesla quarterly results", List.of("official-results")),
                new OpenAiService.GroundingClaim("Tesla product update", List.of("official-product")),
                new OpenAiService.GroundingClaim("Tesla earnings outlook", List.of("reuters")),
                new OpenAiService.GroundingClaim("테슬라 실적 발표", List.of("yonhap")),
                new OpenAiService.GroundingClaim("테슬라 투자 전망", List.of("news1")),
                new OpenAiService.GroundingClaim("Tesla stock tip", List.of("blog")),
                new OpenAiService.GroundingClaim("Nike quarterly results", List.of("nike"))));
        var curated = LiveResearchService.curate(found, List.of("TSLA", "NKE"));
        assertThat(curated.sources()).extracting(OpenAiService.GroundingSource::id)
            .containsExactly("yonhap", "news1", "nike");
        assertThat(curated.claims()).extracting(OpenAiService.GroundingClaim::sourceIds)
            .doesNotContain(List.of("blog"), List.of("official-product"), List.of("official-results"), List.of("reuters"));
    }

    @Test void koreanNewsIsAcceptedAndInvalidSummaryDoesNotEraseValidOne() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("애플 실적 발표", List.of(
            new OpenAiService.GroundingSource("apple", "애플 실적 발표", "https://www.yna.co.kr/view/AKR20261027000100009"),
            new OpenAiService.GroundingSource("bad", "Tesla stock tip", "https://blog.example/tesla")),
            List.of(new OpenAiService.GroundingClaim("애플 실적 발표", List.of("apple"))));
        var curated = LiveResearchService.curate(found, List.of("AAPL"));
        assertThat(curated.sources()).extracting(OpenAiService.GroundingSource::id).containsExactly("apple");
        var raw = mapper.readTree("""
            {"articles":[{"symbol":"AAPL","sourceId":"invented","summary":"허위 요약"},
              {"symbol":"AAPL","sourceId":"apple","summary":"실적 발표를 전했습니다.",
               "growth":"성장 근거","risk":"위험 요인","relevance":"판단 참고"}]}
            """);
        var parsed = LiveResearchService.parseArticles(raw, curated, List.of("AAPL"), LocalDate.now());
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).summary()).isEqualTo("실적 발표를 전했습니다.");
        assertThat(parsed.get(0).kind()).isEqualTo("news");
    }

    @Test void keepsMultipleDistinctFactsForEachSelectedArticle() {
        var found = new OpenAiService.GroundedResult("검색된 사실", List.of(
            new OpenAiService.GroundingSource("apple", "애플 분기 실적", "https://www.yna.co.kr/view/AKR20261027000100009")),
            List.of(new OpenAiService.GroundingClaim("애플 분기 매출이 증가했습니다.", List.of("apple")),
                new OpenAiService.GroundingClaim("애플 서비스 매출이 증가했습니다.", List.of("apple")),
                new OpenAiService.GroundingClaim("애플 서비스 매출이 증가했습니다.", List.of("apple"))));
        var curated = LiveResearchService.curate(found, List.of("AAPL"));
        assertThat(curated.claims()).extracting(OpenAiService.GroundingClaim::text)
            .containsExactly("애플 분기 매출이 증가했습니다.", "애플 서비스 매출이 증가했습니다.");
    }

    @Test void stockAnalysisRejectsOtherStockSourcesButKeepsValidRetry() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("애플과 테슬라 사실", List.of(
            new OpenAiService.GroundingSource("apple", "애플 분기 실적", "https://www.yna.co.kr/view/AKR20261027000100009"),
            new OpenAiService.GroundingSource("tesla", "테슬라 분기 실적", "https://www.news1.kr/articles/tesla-results")),
            List.of(new OpenAiService.GroundingClaim("애플 실적 개선", List.of("apple")),
                new OpenAiService.GroundingClaim("테슬라 비용 증가", List.of("tesla"))));
        var raw = mapper.readTree("""
            {"articles":[
              {"symbol":"AAPL","sourceId":"apple","summary":"애플 실적이 개선됐습니다."},
              {"symbol":"TSLA","sourceId":"tesla","summary":"테슬라 비용이 증가했습니다."}],
             "stockAnalyses":[
              {"symbol":"AAPL","summary":"잘못 연결된 분석","growth":"성장","sourceIds":["tesla"]},
              {"symbol":"AAPL","summary":"실적 개선이 확인됐습니다.","growth":"매출 확대 가능성",
               "risk":"후속 분기 확인 필요","decisionPoint":"보유 시 실적 변화에 노출",
               "nextCheck":"다음 분기 실적","keywords":["실적"],"sourceIds":["apple"]},
              {"symbol":"TSLA","summary":"테슬라 비용이 늘었습니다.","risk":"수익성 압박",
               "sourceIds":["invented"]}]}
            """);
        var articles = LiveResearchService.parseArticles(raw, found, List.of("AAPL", "TSLA"), LocalDate.of(2026, 10, 27));
        var analyses = LiveResearchService.parseStockAnalyses(raw, articles, found, List.of("AAPL", "TSLA"));
        assertThat(analyses).hasSize(1);
        assertThat(analyses.get(0).symbol()).isEqualTo("AAPL");
        assertThat(analyses.get(0).sourceIds()).containsExactly("apple");
        assertThat(analyses.get(0).keywords()).containsExactly("실적");
    }

    @Test void missingKoreanResultsUseOfficialSourcesOnlyForMissingStocks() throws Exception {
        OpenAiService ai = mock(OpenAiService.class);
        var korean = new OpenAiService.GroundedResult("애플과 마이크로소프트 기사", List.of(
            new OpenAiService.GroundingSource("a", "애플 분기 실적", "https://www.yna.co.kr/view/AKR20261027000100009"),
            new OpenAiService.GroundingSource("m", "마이크로소프트 실적", "https://www.news1.kr/articles/msft-results")),
            List.of(new OpenAiService.GroundingClaim("애플 분기 실적", List.of("a")),
                new OpenAiService.GroundingClaim("마이크로소프트 실적", List.of("m"))));
        when(ai.search(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            String version = invocation.getArgument(1);
            if (version.startsWith("korean-pair") && version.contains("AAPL-MSFT")) return korean;
            throw new OpenAiService.AiUnavailable("해당 종목 결과 없음");
        });
        var result = new LiveResearchService(ai, new DemoData(new ObjectMapper())).research();
        assertThat(result.sources()).hasSize(2);
        verify(ai, never()).search(anyString(), argThat(version -> version.startsWith("official-pair")
            && (version.contains("AAPL") || version.contains("MSFT"))), anyList());
        verify(ai, atLeastOnce()).search(anyString(), argThat(version -> version.startsWith("official-pair")
            && version.contains("NVDA")), anyList());
    }

    @Test void followupSearchAddsMissingHoldingWithoutSourceIdCollision() {
        var first = new OpenAiService.GroundedResult("애플 발표", List.of(
            new OpenAiService.GroundingSource("live-0", "Apple quarterly results", "https://www.apple.com/newsroom/apple-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("Apple quarterly results", List.of("live-0"))));
        var second = new OpenAiService.GroundedResult("나이키 발표", List.of(
            new OpenAiService.GroundingSource("live-0", "Nike quarterly results", "https://investors.nike.com/nike-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("Nike quarterly results", List.of("live-0"))));
        var merged = LiveResearchService.curate(LiveResearchService.merge(first, second), List.of("AAPL", "NKE"));
        assertThat(merged.sources()).extracting(OpenAiService.GroundingSource::id).containsExactly("live-0", "live-1");
        assertThat(merged.claims()).extracting(OpenAiService.GroundingClaim::sourceIds)
            .containsExactly(List.of("live-0"), List.of("live-1"));
    }

    @Test void oneFailedSummaryBatchKeepsOtherStocksAnalysis() throws Exception {
        OpenAiService ai = mock(OpenAiService.class);
        ObjectMapper mapper = new ObjectMapper();
        var found = new OpenAiService.GroundedResult("Apple results\nTesla results", List.of(
            new OpenAiService.GroundingSource("live-0", "Apple quarterly results", "https://www.apple.com/newsroom/apple-quarterly-results"),
            new OpenAiService.GroundingSource("live-1", "Tesla quarterly results", "https://ir.tesla.com/tesla-quarterly-results")),
            List.of(new OpenAiService.GroundingClaim("Apple quarterly results", List.of("live-0")),
                new OpenAiService.GroundingClaim("Tesla quarterly results", List.of("live-1"))));
        when(ai.search(anyString(), anyString())).thenReturn(found);
        when(ai.search(anyString(), anyString(), anyList())).thenReturn(found);
        when(ai.jsonSchema(anyString(), anyString(), anyString(), anyMap()))
            .thenReturn(mapper.readTree("""
                {"articles":[{"symbol":"AAPL","sourceId":"live-0","summary":"실적 발표를 확인했습니다.",
                  "growth":"성장 참고","risk":"위험 참고","relevance":"보유 판단 참고"}]}
                """))
            .thenThrow(new OpenAiService.AiUnavailable("OpenAI 응답이 완성되지 않았습니다."));
        var brief = new LiveResearchService(ai, new DemoData(mapper), mapper).brief();
        assertThat(brief.mode()).isEqualTo("structured");
        assertThat(brief.articles()).filteredOn(article -> article.symbol().equals("AAPL"))
            .extracting(LiveResearchService.Article::summary).containsExactly("실적 발표를 확인했습니다.");
        assertThat(brief.articles()).filteredOn(article -> article.symbol().equals("TSLA"))
            .extracting(LiveResearchService.Article::summary).containsExactly("");
        assertThat(brief.message()).contains("일부 자료만");
    }
}
