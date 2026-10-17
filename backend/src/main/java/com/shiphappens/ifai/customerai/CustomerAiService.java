package com.shiphappens.ifai.customerai;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomerAiService {

    public List<DemoScenario> scenarios() {
        return List.of(
            new DemoScenario("normal_day", "Ordinary day, no event, mild weather. The AI should behave sensibly.",
                new InventoryInput("Grill sausages", 80, 120, 30, 4.5, "Mild", false, false, 10, 200, 1),
                new AiDecision(80, 30, 0, 360.0, "LOW", "HIGH", reasoning())),
            new DemoScenario("event_and_sunny", "Local event and warm weather: demand jumps, restock needed.",
                new InventoryInput("Grill sausages", 80, 60, 10, 4.5, "Sunny and Warm", true, true, 10, 200, 1),
                new AiDecision(105, 10, 55, 472.5, "LOW", "LOW", reasoning())),
            new DemoScenario("slow_supplier", "Supplier needs 3 days, so tomorrow's stockout risk should be HIGH.",
                new InventoryInput("Grill sausages", 80, 30, 0, 4.5, "Warm", true, false, 10, 200, 3),
                new AiDecision(95, 0, 0, 427.5, "HIGH", "LOW", reasoning())),
            new DemoScenario("lots_expiring", "Lots of stock expires tomorrow and demand is low: expiration risk should be HIGH.",
                new InventoryInput("Grill sausages", 40, 150, 100, 4.5, "Cold", false, false, 10, 200, 1),
                new AiDecision(40, 100, 0, 180.0, "LOW", "HIGH", reasoning())),
            new DemoScenario("faulty_over_order", "The AI malfunctions and orders far too much, ignoring the supplier limit.",
                new InventoryInput("Grill sausages", 80, 120, 30, 4.5, "Mild", false, false, 10, 200, 1),
                new AiDecision(80, 30, 0, 360.0, "LOW", "HIGH", reasoning()))
        );
    }

    public List<JudgmentResult> runJudgment() {
        return List.of(
            new JudgmentResult(50, null, 6, "Grill sausages", "EXPIRATION_HIGH_RISK", "WARNING"),
            new JudgmentResult(0, null, 7, "Grill sausages", "NORMAL", "PASS"),
            new JudgmentResult(50, null, 8, "Grill sausages", "STOCKOUT_HIGH_RISK", "WARNING"),
            new JudgmentResult(50, null, 9, "Grill sausages", "EXPIRATION_HIGH_RISK", "WARNING"),
            new JudgmentResult(50, null, 10, "Grill sausages", "EXPIRATION_HIGH_RISK", "WARNING")
        );
    }

    private String reasoning() {
        return "Recommendation based on yesterday's sales, events, promotions, stock and supplier limits.";
    }

    public AiDecision analyze(InventoryInput input) {
        int predictedSales = input.yesterdaySales();

        if (input.eventTomorrow()) {
            predictedSales += 15;
        }

        if (input.promotionTomorrow()) {
            predictedSales += 10;
        }

        int usableStock = Math.max(0, input.currentStock() - input.expiringTomorrow());
        int needed = Math.max(0, predictedSales - usableStock);

        int restock = 0;
        if (input.deliveryLeadTimeDays() <= 1 && needed >= input.supplierMinOrder()) {
            restock = Math.min(needed, input.supplierMaxOrder());
        }

        int predictedWaste = Math.min(input.currentStock(), input.expiringTomorrow());

        String stockoutRisk = (usableStock + restock < predictedSales) ? "HIGH" : "LOW";
        String expirationRisk = predictedWaste > 10 ? "HIGH" : "LOW";

        return new AiDecision(
            predictedSales,
            predictedWaste,
            restock,
            predictedSales * input.sellingPrice(),
            stockoutRisk,
            expirationRisk,
            "Recommendation based on yesterday's sales, events, promotions, stock and supplier limits."
        );
    }
}
