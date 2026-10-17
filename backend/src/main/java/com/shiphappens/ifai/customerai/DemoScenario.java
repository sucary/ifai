package com.shiphappens.ifai.customerai;

public record DemoScenario(
    String name,
    String description,
    InventoryInput input,
    AiDecision decision,
    java.util.List<SalesPoint> salesSeries
) {}
