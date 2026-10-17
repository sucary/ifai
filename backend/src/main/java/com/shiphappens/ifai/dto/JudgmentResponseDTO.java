package com.shiphappens.ifai.dto;

import com.shiphappens.ifai.entity.RiskFactor;
import com.shiphappens.ifai.entity.Verdict;

import java.time.LocalDateTime;

public record JudgmentResponseDTO (
         String itemName,
         Integer riskScore,
         Verdict verdict,
         RiskFactor riskFactor,
         LocalDateTime createdAt
) {

}
