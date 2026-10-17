import type { Judgment, Scenario, SalesPoint } from "../types";

// Customer AI scenarios and IF monitoring results are kept behind this API seam.
export async function getScenarios(): Promise<Scenario[]> {
  const response = await fetch("/api/scenarios");
  if (!response.ok) throw new Error(`Could not load scenarios (${response.status})`);
  const items = await response.json();
  return items.map((item: any): Scenario => {
    const { input, decision } = item;
    return {
      id: item.name,
      name: item.name.replaceAll("_", " "),
      date: "API scenario",
      weather: input.tomorrowWeather,
      event: input.eventTomorrow ? "Event tomorrow" : "None",
      promotion: input.promotionTomorrow ? "Promotion tomorrow" : "No promotion",
      currentStock: input.currentStock,
      historicalAverage: input.yesterdaySales,
      predictedSales: decision.predictedSales,
      recommendedOrder: decision.restockQuantity,
      predictedWaste: decision.predictedWaste,
      stockoutRisk: decision.stockoutRisk === "HIGH" ? 100 : 0,
      expirationRisk: decision.expirationRisk === "HIGH" ? 100 : 0,
      supplierDeliveryDays: input.deliveryLeadTimeDays,
      expirationDays: input.expiringTomorrow > 0 ? 1 : 4,
      minimumOrder: input.supplierMinOrder,
      aiReasoning: decision.reasoning,
      verdict: "PASS",
      riskScore: 0,
      riskExposureEur: (decision.predictedWaste + Math.max(0, decision.predictedSales - Math.max(0, input.currentStock - decision.predictedWaste))) * input.sellingPrice,
      riskFactors: [
        decision.stockoutRisk === "HIGH" ? "The decision leaves a high stockout risk." : "Stockout risk is low.",
        decision.expirationRisk === "HIGH" ? `${input.expiringTomorrow} units may expire tomorrow.` : "Expiration risk is low.",
        input.deliveryLeadTimeDays > 1 ? `Supplier delivery takes ${input.deliveryLeadTimeDays} days.` : "Supplier delivery arrives within one day.",
      ],
      suggestedAction: item.description,
      salesHistory: [input.yesterdaySales],
      salesSeries: (item.salesSeries ?? [{ date: new Date().toISOString().slice(0, 10), value: input.yesterdaySales, kind: "historical" }]) as SalesPoint[],
      dataAvailability: "The API currently provides one sales input, not a month-long time series.",
    };
  });
}

export async function runMonitoringCheck(): Promise<Judgment[]> {
  const response = await fetch("/api/judgment/run", { method: "POST" });
  if (!response.ok) throw new Error(`Monitoring check failed (${response.status})`);
  return await response.json();
}
