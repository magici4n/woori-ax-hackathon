package com.woori.tax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsInAnyOrder;

@SpringBootTest(properties = {"app.openai.key="})
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    @Test void portfolioAndManualComparisonWorkWithoutApiKey() throws Exception {
        mvc.perform(get("/api/portfolio")).andExpect(status().isOk())
            .andExpect(jsonPath("$.estimatedTaxKrw").value(594000))
            .andExpect(jsonPath("$.realizedKrw").value(5200000))
            .andExpect(jsonPath("$.rule.fx").value(1362))
            .andExpect(jsonPath("$.positions.length()").value(6))
            .andExpect(jsonPath("$.accounts.length()").value(3))
            .andExpect(jsonPath("$.positions[*].lot.symbol", containsInAnyOrder("AAPL", "MSFT", "NVDA", "INTC", "NKE", "TSLA")));
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content("""
            {"symbol":"TSLA","conditions":{"symbols":{"TSLA":{"excluded":false,"maxShares":10}},"targetReductionKrw":550000},"customQuantity":5}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.maxAllowedQuantity").value(10)).andExpect(jsonPath("$.scenarios[3].sellQuantity").value(5));
        mvc.perform(post("/api/conditions/extract").contentType(MediaType.APPLICATION_JSON).content("""
            {"message":"테슬라는 반만", "existing":{"symbols":{},"targetReductionKrw":null}}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("unavailable")).andExpect(jsonPath("$.canApply").value(false));
    }
    @Test void fractionalSharesAndOverLimitQuantityAreRejected() throws Exception {
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content("""
            {"symbol":"TSLA","customQuantity":0.5}
            """)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content("""
            {"symbol":"TSLA","conditions":{"symbols":{"TSLA":{"excluded":true}}},"customQuantity":1}
            """)).andExpect(status().isBadRequest());
    }
    @Test void portfolioSimulationAndResearchAreAvailableWithoutAi() throws Exception {
        mvc.perform(post("/api/simulation").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scenarios.length()").value(3)).andExpect(jsonPath("$.maxTaxReductionKrw").value(594000));
        mvc.perform(get("/api/research")).andExpect(status().isOk()).andExpect(jsonPath("$.notes.length()").value(3)).andExpect(jsonPath("$.sources.length()").value(7));
        mvc.perform(post("/api/simulation/explain").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("unavailable"));
        mvc.perform(post("/api/simulation/assess").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("unavailable"))
            .andExpect(jsonPath("$.comparison.scenarios.length()").value(3));
    }
    @Test void liveResearchFailureReturnsReadableApiError() throws Exception {
        mvc.perform(post("/api/research/live").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("OpenAI 키가 설정되지 않았습니다. 수동 조건 입력과 계산을 사용할 수 있습니다."));
    }
}
