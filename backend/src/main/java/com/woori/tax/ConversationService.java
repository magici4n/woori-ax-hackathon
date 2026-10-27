package com.woori.tax;

import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import static com.woori.tax.Models.*;

/** 로컬 시연용 대화 상태. 실제 매도 조건은 별도 사용자 확인을 거쳐 적용됩니다. */
@Service
public class ConversationService {
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_SESSIONS = 100;
    private final Clock clock;
    private final Map<String, Context> sessions = new LinkedHashMap<>();

    public ConversationService() { this(Clock.systemUTC()); }
    ConversationService(Clock clock) { this.clock = clock; }

    public static final class Context {
        private final String id = UUID.randomUUID().toString();
        private final Deque<ConversationMessage> history = new ArrayDeque<>();
        private String selectedSymbol;
        private PendingQuestion pending;
        private Instant accessedAt;
        private Context(String selectedSymbol, Instant now) { this.selectedSymbol = selectedSymbol; accessedAt = now; }
        public String id() { return id; }
        public synchronized String selectedSymbol() { return selectedSymbol; }
        public synchronized PendingQuestion pending() { return pending; }
        public synchronized List<ConversationMessage> history() { return List.copyOf(history); }
    }

    public synchronized Context getContext(String id, String selectedSymbol) {
        Instant now = clock.instant();
        sessions.values().removeIf(c -> Duration.between(c.accessedAt, now).compareTo(TTL) >= 0);
        if (id != null && !id.matches("[a-fA-F0-9-]{36}")) throw new IllegalArgumentException("대화 ID 형식이 올바르지 않습니다.");
        Context context = id == null ? null : sessions.get(id);
        if (context == null) {
            if (sessions.size() >= MAX_SESSIONS) sessions.remove(sessions.keySet().iterator().next());
            context = new Context(selectedSymbol, now);
            sessions.put(context.id, context);
        }
        synchronized (context) {
            if (!Objects.equals(context.selectedSymbol, selectedSymbol)) {
                // 종목 변경 전의 질문과 짧은 "응"을 새 종목에 연결하지 않습니다.
                context.pending = null;
                context.history.clear();
                context.selectedSymbol = selectedSymbol;
            }
            context.accessedAt = now;
        }
        return context;
    }

    public void appendMessage(Context context, String role, String text) {
        if (!Set.of("user", "assistant").contains(role)) throw new IllegalArgumentException("대화 역할 오류");
        synchronized (context) {
            context.history.addLast(new ConversationMessage(role, text.substring(0, Math.min(text.length(), 1000))));
            while (context.history.size() > 12) context.history.removeFirst();
        }
    }

    public void setPendingQuestion(Context context, ConditionPatch patch, String question) {
        synchronized (context) {
            context.pending = new PendingQuestion(UUID.randomUUID().toString(), patch, question, context.selectedSymbol);
        }
    }
    public void clearPendingQuestion(Context context) { synchronized (context) { context.pending = null; } }
    public synchronized void reset(String id) { sessions.remove(id); }

    public List<ConversationMessage> recentMessages(String id) {
        Context context;
        synchronized (this) {
            if (id == null) return List.of();
            if (!id.matches("[a-fA-F0-9-]{36}")) throw new IllegalArgumentException("대화 ID 형식이 올바르지 않습니다.");
            context = sessions.get(id);
            if (context == null || Duration.between(context.accessedAt, clock.instant()).compareTo(TTL) >= 0)
                return List.of();
        }
        return context.history();
    }

    /** 짧고 명확한 긍정/부정만 처리합니다. 종목·수량을 덧붙인 문장은 AI 검증 경로로 보냅니다. */
    public Optional<Boolean> resolvePendingAnswer(Context context, String message) {
        String normalized = message.replaceAll("[\\s.!?,。！？]", "");
        boolean yes = normalized.matches("(응|네|예|그래|좋아|좋아요|맞아|맞아요)(그종목)?(제외해줘|제외해주세요|적용해줘|그렇게해줘)?")
            || normalized.matches("(그종목)?(제외해줘|제외해주세요)");
        boolean no = Set.of("아니", "아니요", "아냐", "취소", "취소해줘", "제외하지마", "제외하지말아줘").contains(normalized);
        return yes ? Optional.of(true) : no ? Optional.of(false) : Optional.empty();
    }
}
