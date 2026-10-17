public class Scenario {
    package com.shiphappens.ifai.customerai;

/** One test case loaded from scenarios.json. */
public record Scenario(String name, String description, InventoryInput input) {}
}
