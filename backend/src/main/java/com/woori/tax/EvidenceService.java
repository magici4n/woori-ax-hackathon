package com.woori.tax;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static com.woori.tax.Models.*;

@Service
public class EvidenceService {
    private final List<Source> sources;
    private final List<ResearchNote> notes;
    public EvidenceService(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("evidence.json").getInputStream()) {
            sources = mapper.readValue(stream, new TypeReference<>() {});
        }
        try (var stream = new ClassPathResource("research-notes.json").getInputStream()) {
            notes = mapper.readValue(stream, new TypeReference<>() {});
        }
        for (ResearchNote note : notes) {
            if (sources.stream().noneMatch(s -> s.id().equals(note.sourceId()) && s.symbols().contains(note.symbol())))
                throw new IOException("기업 분석의 출처를 확인하세요: " + note.sourceId());
        }
    }
    // 작은 자료집에는 메타데이터 필터로 충분합니다. 사용자 문장이나 자료 속 지시문은 실행하지 않습니다.
    public List<Source> search(String symbol, LocalDate until, Set<String> kinds) {
        return sources.stream().filter(s -> kinds.contains(s.kind()))
            .filter(s -> s.symbols().isEmpty() || s.symbols().contains(symbol))
            .filter(s -> s.publishedAt() == null || !LocalDate.parse(s.publishedAt()).isAfter(until))
            .limit(5).toList();
    }
    public List<Source> all(LocalDate until) { return sources.stream().filter(s -> s.publishedAt() == null || !LocalDate.parse(s.publishedAt()).isAfter(until)).toList(); }
    public List<ResearchNote> notes() { return notes; }
    public String version() { return "evidence-2026-10-27-v3"; }
}
