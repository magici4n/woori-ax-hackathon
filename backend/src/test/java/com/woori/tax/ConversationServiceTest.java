package com.woori.tax;

import org.junit.jupiter.api.Test;
import java.time.*;
import static com.woori.tax.Models.*;
import static org.assertj.core.api.Assertions.*;

class ConversationServiceTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-27T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @Test void symbolChangeClearsQuestionAndHistory() {
        var service = new ConversationService();
        var context = service.getContext(null, "TSLA");
        service.appendMessage(context, "user", "장기투자 중");
        service.setPendingQuestion(context, new ConditionPatch("TSLA", true, null, null), "제외할까요?");
        var changed = service.getContext(context.id(), "AAPL");
        assertThat(changed.pending()).isNull();
        assertThat(changed.history()).isEmpty();
        assertThat(changed.selectedSymbol()).isEqualTo("AAPL");
    }
    @Test void sessionsExpireAndQuestionsCannotBeRevived() {
        var clock = new MutableClock(); var service = new ConversationService(clock);
        var old = service.getContext(null, "TSLA");
        service.setPendingQuestion(old, new ConditionPatch("TSLA", true, null, null), "제외할까요?");
        clock.now = clock.now.plus(Duration.ofMinutes(31));
        var fresh = service.getContext(old.id(), "TSLA");
        assertThat(fresh.id()).isNotEqualTo(old.id());
        assertThat(fresh.pending()).isNull();
    }
    @Test void historyIsBoundedAndSessionsAreIsolated() {
        var service = new ConversationService();
        var first = service.getContext(null, "TSLA"); var second = service.getContext(null, "TSLA");
        for (int i = 0; i < 30; i++) service.appendMessage(first, "user", "message-" + i);
        assertThat(first.history()).hasSize(12);
        assertThat(first.history().get(0).text()).isEqualTo("message-18");
        assertThat(second.history()).isEmpty();
    }
    @Test void resetDeletesOnlyRequestedConversation() {
        var service = new ConversationService();
        var first = service.getContext(null, "TSLA"); var second = service.getContext(null, "AAPL");
        service.reset(first.id());
        assertThat(service.getContext(first.id(), "TSLA").id()).isNotEqualTo(first.id());
        assertThat(service.getContext(second.id(), "AAPL").id()).isEqualTo(second.id());
    }
}
