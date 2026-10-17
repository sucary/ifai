package com.shiphappens.ifai.customerai;

public record Scenario(
        String name,
        String description,
        boolean faulty,
        InventoryInput input
) {}
