package com.shiphappens.ifai.service;

import com.shiphappens.ifai.customerai.AiDecision;
import com.shiphappens.ifai.entity.JudgmentResult;
import com.shiphappens.ifai.entity.Verdict;
import com.shiphappens.ifai.entity.RiskFactor;
import com.shiphappens.ifai.repository.JudgmentResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JudgmentService {

    private final JudgmentResultRepository judgmentResultRepository;

    @Transactional
    public JudgmentResult evaluateAndSave(String itemName, AiDecision ai) {
        // 1. calculate the risk score
        int riskScore = 0;
        if ("HIGH".equalsIgnoreCase(ai.stockoutRisk())) riskScore += 50;
        if ("HIGH".equalsIgnoreCase(ai.expirationRisk())) riskScore += 30;
        if (ai.predictedWaste() > 10) riskScore += 20;

        // 2. Verdict
        Verdict verdict = (riskScore >= 50) ? Verdict.WARNING : Verdict.PASS;

        // 3. RiskFactor
        RiskFactor riskFactor = RiskFactor.NORMAL;
        if ("HIGH".equalsIgnoreCase(ai.stockoutRisk())) {
            riskFactor = RiskFactor.STOCKOUT_HIGH_RISK;
        } else if ("HIGH".equalsIgnoreCase(ai.expirationRisk())) {
            riskFactor = RiskFactor.EXPIRATION_HIGH_RISK;
        }

        // 4.
        JudgmentResult result = JudgmentResult.of(
                itemName,
                riskScore,
                verdict,
                riskFactor
        );

        return judgmentResultRepository.save(result);
    }
}