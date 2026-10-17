package com.shiphappens.ifai.customerai;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomerAiService {

    private final TurkuDatabaseService database;

    public CustomerAiService(TurkuDatabaseService database) {
        this.database = database;
    }

    public List<DemoScenario> scenarios() {
        return database.scenarios();
    }

    public List<JudgmentResult> runJudgment() {
        return database.scenarios().stream().map((scenario) -> {
            AiDecision decision = scenario.decision();
            boolean highExpiration = "HIGH".equals(decision.expirationRisk());
            boolean highStockout = "HIGH".equals(decision.stockoutRisk());
            String factor = highExpiration ? "EXPIRATION_HIGH_RISK" : highStockout ? "STOCKOUT_HIGH_RISK" : "NORMAL";
            int score = (highExpiration || highStockout) ? 50 : 0;
            String verdict = score == 0 ? "PASS" : "WARNING";
            return new JudgmentResult(score, java.time.Instant.now().toString(), scenario.name().hashCode(), scenario.input().product(), factor, verdict);
        }).toList();
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
