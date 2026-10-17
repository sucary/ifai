import type { Scenario } from "../types";

// Backend integration seam:
// - GET /api/demo/scenarios -> Scenario[]
// - GET /api/demo/scenarios/{id} -> Scenario
// - POST /api/monitoring/evaluate -> monitoring result
// Keep the UI dependent on these functions rather than on fetch calls directly.
export async function getScenarios(): Promise<Scenario[]> {
  // Replace with fetch("/api/demo/scenarios") when the backend endpoint exists.
  const { demoScenarios } = await import("../data/demoData");
  return demoScenarios;
}

export async function runMonitoringCheck(scenario: Scenario): Promise<Scenario> {
  // Replace with a POST request carrying the customer AI decision and source data.
  // The mocked delay lets the demo show the monitoring workflow.
  await new Promise((resolve) => setTimeout(resolve, 1200));
  return scenario;
}
