package com.shiphappens.ifai.dto;

public record AiDecisionDTO(
        int predictedSales,
        int predictedWaste,
        int restockQuantity,
        double estimatedRevenue,
        String stockoutRisk,
        String expirationRisk,
        String reasoning
) {}