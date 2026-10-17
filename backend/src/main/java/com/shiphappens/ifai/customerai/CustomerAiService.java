package com.shiphappens.ifai.customerai;

import org.springframework.stereotype.Service;

@Service
public class CustomerAiService {

    public AiDecision analyze(InventoryInput input) {

        int predictedSales = input.yesterdaySales();

        if (input.eventTomorrow()) {
            predictedSales += 15;
        }

        if (input.promotionTomorrow()) {
            predictedSales += 10;
        }

        int usableStock = Math.max(
            0,
            input.currentStock() - input.expiringTomorrow()
        );

        int needed = Math.max(0, predictedSales - usableStock);

        int restock = 0;

        // Only recommend ordering if delivery arrives in time.
        if (input.deliveryLeadTimeDays() <= 1
                && needed >= input.supplierMinOrder()) {
            restock = Math.min(
                needed,
                input.supplierMaxOrder()
            );
        }

        int predictedWaste = Math.min(
            input.currentStock(),
            input.expiringTomorrow()
        );

        String stockoutRisk =
            usableStock + restock < predictedSales
                ? "HIGH" : "LOW";

        String expirationRisk =
            predictedWaste > 10 ? "HIGH" : "LOW";

        return new AiDecision(
            predictedSales,
            predictedWaste,
            restock,
            predictedSales * input.sellingPrice(),
            stockoutRisk,
            expirationRisk,
            "Recommendation based on yesterday's sales, "
                + "events, promotions, stock and supplier limits."
        );
    }
}