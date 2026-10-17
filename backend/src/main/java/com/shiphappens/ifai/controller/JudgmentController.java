package com.shiphappens.ifai.controller;

import com.shiphappens.ifai.customerai.ScenarioResult;
import com.shiphappens.ifai.customerai.ScenarioService;
import com.shiphappens.ifai.entity.JudgmentResult;
import com.shiphappens.ifai.service.JudgmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/judgment")
@RequiredArgsConstructor
public class JudgmentController {

    private final ScenarioService scenarioService;
    private final JudgmentService judgmentService;

    @PostMapping("/run")
    public List<JudgmentResult> runJudgmentFromScenarios() {
        List<ScenarioResult> scenarioResults = scenarioService.runAll();

        return scenarioResults.stream()
                .map(judgmentService::evaluateAndSave)
                .toList();
    }
}