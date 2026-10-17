package com.shiphappens.ifai.customerai;

import org.springframework.stereotype.Service;

@Service
public class CustomerAiService {

    public AiDecision analyze(InventoryInput input) {
        return analyze(input, false);
    }

    /** The fault switch is used only by the monitoring demo. */
    public AiDecision analyze(InventoryInput input, boolean faulty) {

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

        if (faulty) {
            restock = Math.max(input.supplierMaxOrder() + 1, needed * 2);
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

        String reasoning = faulty
                ? "The customer's AI response is intentionally faulty for this monitoring scenario."
                : "Recommendation based on yesterday's sales, events, promotions, stock and supplier limits.";

        return new AiDecision(
                predictedSales,
                predictedWaste,
                restock,
                predictedSales * input.sellingPrice(),
                stockoutRisk,
                expirationRisk,
                reasoning
        );
    }
}
