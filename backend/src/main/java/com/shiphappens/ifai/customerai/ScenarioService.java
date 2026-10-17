package com.shiphappens.ifai.customerai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/** Loads test scenarios from src/main/resources/scenarios.json (no OpenAI needed). */
@Service
public class ScenarioService {

    private final CustomerAiService ai;
    private final List<Scenario> scenarios;

    public ScenarioService(CustomerAiService ai, ObjectMapper mapper) throws IOException {
        this.ai = ai;
        try (InputStream in = new ClassPathResource("scenario.json").getInputStream()) {
            this.scenarios = mapper.readValue(in, new TypeReference<List<Scenario>>() {});
        }
    }

    public List<ScenarioResult> runAll() {
        return scenarios.stream().map(this::run).toList();
    }

    public Optional<ScenarioResult> run(String name) {
        return scenarios.stream()
                .filter(s -> s.name().equalsIgnoreCase(name))
                .findFirst()
                .map(this::run);
    }

    private ScenarioResult run(Scenario s) {
        return new ScenarioResult(s.name(), s.description(), s.input(), ai.analyze(s.input(), s.faulty()));
    }
}
