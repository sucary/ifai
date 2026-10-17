package com.shiphappens.ifai.dto;

public record BusinessRuleDTO(
        double warningThreshold,
        double blockThreshold,
        int maxAllowableWaste
) {}