package com.woori.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LiveResearchServiceTest {
    @Test void researchIsReusedByLaterConsumersWithoutAnotherPaidSearch() throws Exception {
        OpenAiService ai = mock(OpenAiService.class);
        var found = new OpenAiService.GroundedResult("확인된 사실",
            List.of(new OpenAiService.GroundingSource("live-0", "IR", "https://example.com")),
            List.of(new OpenAiService.GroundingClaim("확인된 사실", List.of("live-0"))));
        when(ai.search(anyString(), anyString())).thenReturn(found);
        var service = new LiveResearchService(ai, new DemoData(new ObjectMapper()));
        assertThat(service.current()).isNull();
        assertThat(service.research()).isSameAs(found);
        assertThat(service.current()).isSameAs(found);
        assertThat(service.research()).isSameAs(found);
        verify(ai, times(1)).search(anyString(), anyString());
    }
}
