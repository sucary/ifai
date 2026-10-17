package com.shiphappens.ifai.customerai;

public record AiDecision(
        int predictedSales,
        int predictedWaste,
        int restockQuantity,
        double estimatedRevenue,
        String stockoutRisk,
        String expirationRisk,
        String reasoning
) {}
