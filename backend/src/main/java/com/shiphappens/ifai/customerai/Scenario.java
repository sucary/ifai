package com.shiphappens.ifai.customerai;

public record Scenario(
        String name,
        String description,
        InventoryInput input
) {}