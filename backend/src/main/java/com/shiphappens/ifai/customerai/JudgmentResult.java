package com.shiphappens.ifai.customerai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JudgmentResult(
    @JsonProperty("RiskScore") int riskScore,
    String createdAt,
    int id,
    String itemName,
    String riskFactor,
    String verdict
) {}
