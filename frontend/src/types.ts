export type Verdict = "PASS" | "WARNING" | "BLOCK";

export type Scenario = {
  id: string;
  name: string;
  date: string;
  weather: string;
  event: string;
  promotion: string;
  currentStock: number;
  historicalAverage: number;
  predictedSales: number;
  recommendedOrder: number;
  predictedWaste: number;
  stockoutRisk: number;
  expirationRisk: number;
  supplierDeliveryDays: number;
  expirationDays: number;
  minimumOrder: number;
  aiReasoning: string;
  verdict: Verdict;
  riskScore: number;
  riskFactors: string[];
  suggestedAction: string;
  salesHistory: number[];
};
