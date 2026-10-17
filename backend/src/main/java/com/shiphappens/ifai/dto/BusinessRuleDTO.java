package com.shiphappens.ifai.dto;

public record BusinessRuleDTO(
        double warningThreshold,   // 경고 판정 임계치 (예: 50.0)
        double blockThreshold,     // 차단/위험 판정 임계치 (예: 80.0)
        int maxAllowableWaste      // 허용 가능한 최대 폐기량 기준
) {}