package com.woori.tax;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
import static com.woori.tax.Models.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final DemoData data;
    private final ScenarioService scenarios;
    private final AssistantService assistant;
    private final GeminiService ai;
    private final ConversationService conversations;
    private final PortfolioSimulationService simulation;
    private final EvidenceService evidence;
    public ApiController(DemoData data, ScenarioService scenarios, AssistantService assistant, GeminiService ai, ConversationService conversations, PortfolioSimulationService simulation, EvidenceService evidence) {
        this.data = data; this.scenarios = scenarios; this.assistant = assistant; this.ai = ai; this.conversations = conversations;
        this.simulation = simulation; this.evidence = evidence;
    }
    @GetMapping("/portfolio") public Map<String,Object> portfolio() {
        var positions = data.lots.stream().map(l -> {
            var value = l.priceUsd().multiply(data.rule.fx()).multiply(java.math.BigDecimal.valueOf(l.quantity()));
            return Map.<String,Object>of("lot", l, "marketValueKrw", ScenarioService.won(value),
                "unrealizedKrw", ScenarioService.won(value.subtract(l.acquisitionKrw())),
                "dollarPnl", l.priceUsd().subtract(l.buyUsd()).multiply(java.math.BigDecimal.valueOf(l.quantity())),
                "hypotheticalTaxGainKrw", ScenarioService.won(scenarios.perShareTaxGain(l).multiply(java.math.BigDecimal.valueOf(l.quantity()))));
        }).toList();
        var marketValue = data.lots.stream().map(l -> l.priceUsd().multiply(data.rule.fx()).multiply(java.math.BigDecimal.valueOf(l.quantity()))).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var acquisition = data.lots.stream().map(Lot::acquisitionKrw).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return Map.of("positions", positions, "accounts", data.accounts, "rule", data.rule, "realizedKrw", scenarios.baselineGain(),
            "estimatedTaxKrw", scenarios.tax(scenarios.baselineGain()), "marketValueKrw", ScenarioService.won(marketValue),
            "unrealizedKrw", ScenarioService.won(marketValue.subtract(acquisition)), "demo", true);
    }
    @PostMapping("/scenarios") public Comparison compare(@RequestBody ScenarioRequest request) { return scenarios.compare(request); }
    @PostMapping("/simulation") public PortfolioComparison simulate(@RequestBody SimulationRequest request) { return simulation.simulate(request); }
    @PostMapping("/simulation/explain") public Map<String,Object> explainSimulation(@RequestBody SimulationRequest request) {
        return assistant.explainPortfolio(simulation.simulate(request));
    }
    @GetMapping("/research") public Map<String,Object> research() {
        return Map.of("notes", evidence.notes(), "sources", evidence.all(java.time.LocalDate.parse(data.rule.asOf())), "asOf", data.rule.asOf());
    }
    @PostMapping("/conditions/extract") public Map<String,Object> extract(@RequestBody ExtractRequest request) { return assistant.extract(request); }
    @DeleteMapping("/conversations/{id}") public Map<String,String> resetConversation(@PathVariable String id) {
        conversations.reset(id);
        return Map.of("message", "대화가 초기화되었습니다. 적용 중인 조건은 유지됩니다.");
    }
    @PostMapping("/explain") public Map<String,Object> explain(@RequestBody ExplainRequest request) { return assistant.explain(request); }
    @GetMapping("/evidence") public List<Source> evidence(@RequestParam String symbol) {
        if (!data.symbols().contains(symbol)) throw new IllegalArgumentException("보유 종목을 선택해 주세요.");
        return assistant.sources(symbol);
    }
    @GetMapping("/ai/usage") public Map<String,Object> usage() { return ai.status(); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
}
