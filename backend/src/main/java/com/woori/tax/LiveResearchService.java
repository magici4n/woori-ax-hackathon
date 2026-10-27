package com.woori.tax;

import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class LiveResearchService {
    private final OpenAiService ai;
    private final DemoData data;
    private OpenAiService.GroundedResult result;
    private Instant fetchedAt;
    private LocalDate date;

    public LiveResearchService(OpenAiService ai, DemoData data) {
        this.ai = ai; this.data = data;
    }

    public synchronized OpenAiService.GroundedResult current() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return result != null && date.equals(today) && fetchedAt.isAfter(Instant.now().minus(Duration.ofMinutes(30))) ? result : null;
    }

    public synchronized OpenAiService.GroundedResult research() {
        OpenAiService.GroundedResult cached = current();
        if (cached != null) return cached;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String prompt = "오늘 날짜는 " + today + "입니다. 다음 미국 상장 기업의 최근 발표와 향후 실적발표 일정을 실제 웹 검색으로 조사하세요: "
            + String.join(", ", data.symbols()) + ". 특히 손실 실현 후보의 매도 보류 이유와 매도 위험을 함께 조사하세요. "
            + "기업 IR/공시를 우선하고, 최근 뉴스와 날짜가 명시된 PER 등 밸류에이션 자료가 있으면 구분하세요. "
            + "실적발표 예정일은 공식 확인이 가능할 때만 날짜를 쓰고, 추정 일정은 추정이라고 표시하세요. "
            + "종목별로 사실, 발표/기사 날짜, 성장 근거, 위험 근거, 향후 이벤트를 짧게 한국어로 정리하세요. "
            + "검색되지 않은 항목은 확인 불가로 표시하세요. 투자 권유나 매도 수량 계산은 하지 마세요.";
        OpenAiService.GroundedResult found = ai.search(prompt, today.toString());
        if (found.sources().isEmpty() || found.claims().isEmpty()) throw new OpenAiService.AiUnavailable("출처와 연결된 최신 자료를 얻지 못했습니다.");
        result = found; fetchedAt = Instant.now(); date = today;
        return result;
    }
}
