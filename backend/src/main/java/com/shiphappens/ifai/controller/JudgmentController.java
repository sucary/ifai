package com.shiphappens.ifai.controller;

import com.shiphappens.ifai.customerai.AiDecision;
import com.shiphappens.ifai.customerai.CustomerAiService;
import com.shiphappens.ifai.customerai.InventoryInput;
import com.shiphappens.ifai.entity.JudgmentResult;
import com.shiphappens.ifai.service.JudgmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/judgment")
@RequiredArgsConstructor
public class JudgmentController {

    private final CustomerAiService customerAiService;
    private final JudgmentService judgmentService;

    @PostMapping("/run")
    public JudgmentResult runJudgment(@RequestBody InventoryInput input) {
        AiDecision aiDecision = customerAiService.analyze(input);

        return judgmentService.evaluateAndSave(input.product(), aiDecision);
    }
}