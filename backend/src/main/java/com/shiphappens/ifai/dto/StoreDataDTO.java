package com.shiphappens.ifai.dto;

public record StoreDataDTO(
        Long storeId,
        Long productId,
        int recordedOnHandUnits,
        int saleableNowUnits,
        int safetyStock
) {}