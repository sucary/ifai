package com.shiphappens.ifai.dto;


public record JudgmentRequestDTO(
        Long runId,                    // ai_runs 테이블의 PK (어떤 AI 실행 건인지)
        Long policyId,                 // monitoring_policies 테이블의 PK (어떤 정책을 적용할지)
        AiDecisionDTO aiDecision,      // AI가 분석한 예측 결과
        StoreDataDTO storeData,        // 매장 실제 재고 상태
        BusinessRuleDTO businessRule   // 비즈니스 판정 룰
) {}