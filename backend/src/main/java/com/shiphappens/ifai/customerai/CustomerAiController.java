package com.shiphappens.ifai.customerai;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CustomerAiController {

    private final CustomerAiService service;

    public CustomerAiController(CustomerAiService service) {
        this.service = service;
    }

    @PostMapping("/customer-ai/analyze")
    public AiDecision analyze(@RequestBody InventoryInput input) {
        return service.analyze(input);
    }

    @GetMapping("/scenarios")
    public List<DemoScenario> scenarios() {
        return service.scenarios();
    }

    @PostMapping("/judgment/run")
    public List<JudgmentResult> runJudgment() {
        return service.runJudgment();
    }
}
