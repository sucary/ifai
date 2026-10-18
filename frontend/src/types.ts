export type Verdict = "PASS" | "WARNING" | "BLOCK";

export type Scenario = {
  id: string;
  name: string;
  description?: string;
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
  riskExposureEur?: number;
  riskFactors: string[];
  riskStatuses?: Record<string, "pass" | "warning">;
  suggestedAction: string;
  salesHistory: number[];
  salesSeries?: SalesPoint[];
  dataAvailability?: string;
  judgmentRiskFactor?: string;
};

export type SalesPoint = {
  date: string;
  value: number;
  kind: "historical" | "forecast";
};

export type Judgment = {
  RiskScore: number;
  createdAt: string | null;
  id: number;
  itemName: string;
  riskFactor: string;
  verdict: Verdict;
};
