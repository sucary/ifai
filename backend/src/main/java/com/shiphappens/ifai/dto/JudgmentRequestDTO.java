package com.shiphappens.ifai.dto;


public record JudgmentRequestDTO(
        Long runId,
        Long policyId,
        AiDecisionDTO aiDecision,
        StoreDataDTO storeData,
        BusinessRuleDTO businessRule
) {}