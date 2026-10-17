package com.shiphappens.ifai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "judgment_result")
public class JudgmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemName;

    private Integer RiskScore;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    @Enumerated(EnumType.STRING)
    private RiskFactor riskFactor;

    private LocalDateTime createdAt = LocalDateTime.now();

}