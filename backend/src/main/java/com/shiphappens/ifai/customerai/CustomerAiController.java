package com.shiphappens.ifai.customerai;

import org.springframework.web.bind.annotation.*;
import com.shiphappens.ifai.service.JudgmentService;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CustomerAiController {

    private final CustomerAiService customerAiService;
    private final ScenarioService scenarioService;
    private final JudgmentService judgmentService;

    public CustomerAiController(CustomerAiService customerAiService, ScenarioService scenarioService, JudgmentService judgmentService) {
        this.customerAiService = customerAiService;
        this.scenarioService = scenarioService;
        this.judgmentService = judgmentService;
    }

    @PostMapping("/customer-ai/analyze")
    public AiDecision analyze(@RequestBody InventoryInput input) {
        return customerAiService.analyze(input);
    }

    @GetMapping("/scenarios")
    public List<ScenarioResult> scenarios() {
        return scenarioService.runAll();
    }

    @PostMapping("/judgment/run")
    public List<com.shiphappens.ifai.entity.JudgmentResult> runJudgment() {
        return scenarioService.runAll().stream().map(judgmentService::evaluateAndSave).toList();
    }
}
