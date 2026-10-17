package com.shiphappens.ifai.customerai;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-ai")
public class CustomerAiController {

    private final CustomerAiService service;

    public CustomerAiController(CustomerAiService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    public AiDecision analyze(
            @RequestBody InventoryInput input) {

        return service.analyze(input);
    }
}