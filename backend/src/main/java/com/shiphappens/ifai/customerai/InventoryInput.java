package com.shiphappens.ifai.customerai;

public record InventoryInput(
    String product,
    int yesterdaySales,
    int currentStock,
    int expiringTomorrow,
    double sellingPrice,
    String tomorrowWeather,
    boolean eventTomorrow,
    boolean promotionTomorrow,
    int supplierMinOrder,
    int supplierMaxOrder,
    int deliveryLeadTimeDays
) {}