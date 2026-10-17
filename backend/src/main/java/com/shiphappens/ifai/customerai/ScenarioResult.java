public class ScenarioResult {
    package com.shiphappens.ifai.customerai;

/** A scenario together that could be working with the decision aswell*/
public record ScenarioResult(String name, String
     description, InventoryInput input, AiDecision decision) {}
}
