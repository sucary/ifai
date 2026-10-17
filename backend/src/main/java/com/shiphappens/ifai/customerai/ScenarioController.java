package com.shiphappens.ifai.controller;

import com.shiphappens.ifai.customerai.ScenarioResult;
import com.shiphappens.ifai.customerai.ScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    @GetMapping
    public List<ScenarioResult> getAllScenarios() {
        return scenarioService.runAll();
    }
}