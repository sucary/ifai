import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, ArrowUpRight, Check, ChevronDown, ShieldCheck, ShieldX } from "lucide-react";
import { getScenarios, runMonitoringCheck } from "./services/api";
import type { Judgment, SalesPoint, Scenario, Verdict } from "./types";

const verdictCopy: Record<Verdict, { label: string; className: string }> = {
  PASS: { label: "Approved", className: "pass" },
  WARNING: { label: "Review required", className: "warning" },
  BLOCK: { label: "Blocked", className: "block" },
};

function App() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selectedId, setSelectedId] = useState("TKU_001");
  const [status, setStatus] = useState<"idle" | "checking" | "complete">("idle");
  const [error, setError] = useState<string | null>(null);
  const [judgments, setJudgments] = useState<Judgment[]>([]);

  useEffect(() => {
    getScenarios().then(setScenarios).catch((reason: Error) => setError(reason.message));
  }, []);

  const scenario = useMemo(
    () => scenarios.find((item) => item.id === selectedId) ?? scenarios[0],
    [scenarios, selectedId],
  );

  if (!scenario) return <main className="loading-screen">Loading database data…</main>;

  const runCheck = async () => {
    setStatus("checking");
    setError(null);
    try {
      setJudgments(await runMonitoringCheck());
      setStatus("complete");
    } catch (reason) {
      setStatus("idle");
      setError(reason instanceof Error ? reason.message : "Monitoring check failed.");
    }
  };

  const judgment = judgments[scenarios.indexOf(scenario)];
  const effectiveScenario = judgment ? { ...scenario, verdict: judgment.verdict, riskScore: judgment.RiskScore, judgmentRiskFactor: judgment.riskFactor } : scenario;
  const verdict = verdictCopy[effectiveScenario.verdict];
  const salesSeries = scenario.salesSeries ?? [{ date: scenario.date, value: scenario.historicalAverage, kind: "historical" as const }];

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand"><span className="brand-mark">if</span><span>inventory assurance</span></div>
        <div className="demo-label"><span className="live-dot" /> Monitoring demo</div>
      </header>

      <section className="hero">
        <div>
          <p className="eyebrow">CUSTOMER AI OVERSIGHT</p>
          <h1>Can this inventory decision<br /><em>be trusted?</em></h1>
          <p className="hero-copy">If checks an external AI's restocking decision against real-world business rules before it reaches the supply chain.</p>
        </div>
        <div className="scenario-picker">
          <label htmlFor="scenario">Database scenario</label>
          <div className="select-wrap">
            <select id="scenario" value={scenario.id} onChange={(event) => { setSelectedId(event.target.value); setStatus("idle"); }}>
              {scenarios.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
            <ChevronDown size={16} />
          </div>
          <span>{scenario.description ?? scenario.date}</span>
        </div>
      </section>

      <section className="metric-grid">
        <Metric label="Current stock" value={`${scenario.currentStock}`} suffix="units" />
        <Metric label="Predicted sales" value={`${scenario.predictedSales}`} suffix="units" tone="blue" />
        <Metric label="AI recommended order" value={`${scenario.recommendedOrder}`} suffix="units" tone="orange" />
        <Metric label="Risk score" value={`${effectiveScenario.riskScore}`} suffix="/ 100" detail={effectiveScenario.riskExposureEur !== undefined ? `≈ €${effectiveScenario.riskExposureEur.toFixed(2)} exposure` : undefined} tone={effectiveScenario.verdict === "PASS" ? "green" : "red"} />
      </section>

      <div className="content-grid">
        <section className="card input-card">
          <div className="section-heading"><div><p className="eyebrow">01 / CONTEXT</p><h2>What If received</h2></div><span className="tag">Turku data</span></div>
          <div className="data-list">
            <DataRow label="Weather" value={scenario.weather} />
            <DataRow label="Event" value={scenario.event} />
            <DataRow label="Promotion" value={scenario.promotion} />
            <DataRow label="Historical average" value={`${scenario.historicalAverage} units / day`} />
            <DataRow label="Supplier delivery" value={`${scenario.supplierDeliveryDays} days`} />
            <DataRow label="Product life" value={`${scenario.expirationDays} days`} />
          </div>
        </section>

        <section className="card ai-card">
          <div className="section-heading"><div><p className="eyebrow">02 / EXTERNAL SYSTEM</p><h2>Customer AI decision</h2></div><span className="tag purple">Database forecast</span></div>
          <div className="ai-order"><span>Recommended order</span><strong>{scenario.recommendedOrder}<small> units</small></strong></div>
          <p className="muted">“{scenario.aiReasoning}”</p>
          <div className="mini-stats"><MiniStat label="Expected waste" value={`${scenario.predictedWaste} units`} /><MiniStat label="Stockout risk" value={`${scenario.stockoutRisk}%`} /><MiniStat label="Expiration risk" value={`${scenario.expirationRisk}%`} /></div>
        </section>
      </div>

      <section className="card chart-card">
        <div className="section-heading"><div><p className="eyebrow">SALES SIGNAL</p><h2>Historical sales vs. forecast</h2></div><div className="legend"><span className="legend-dot history" />Historical <span className="legend-dot forecast" />Forecast</div></div>
        <p className="muted data-note">Historical bars are recorded sales. Forecast bars are calculated future estimates, separated by date.</p>
        <div className="chart" aria-label="Historical sales and forecast chart">
          {salesSeries.map((point) => <SalesBar point={point} key={`${point.date}-${point.kind}`} max={Math.max(...salesSeries.map((item) => item.value), 1)} />)}
        </div>
      </section>

      <section className={`verdict-card ${verdict.className}`}>
        <div className="verdict-icon">{effectiveScenario.verdict === "PASS" ? <ShieldCheck /> : effectiveScenario.verdict === "WARNING" ? <AlertTriangle /> : <ShieldX />}</div>
        <div className="verdict-main"><p className="eyebrow">03 / IF MONITORING VERDICT</p><div className="verdict-title"><h2>{effectiveScenario.verdict}</h2><span>{verdict.label}</span></div><p>{effectiveScenario.suggestedAction}</p>{effectiveScenario.judgmentRiskFactor && <p className="muted">Signal: {effectiveScenario.judgmentRiskFactor}</p>}</div>
        <button className="primary-button" onClick={runCheck} disabled={status === "checking"}>{status === "checking" ? "Checking decision…" : status === "complete" ? "Check complete" : "Run monitoring check"}<ArrowUpRight size={17} /></button>
      </section>

      <div className="bottom-grid">
        <section className="card risk-card"><div className="section-heading"><div><p className="eyebrow">RISK EXPLANATION</p><h2>What went wrong?</h2></div><div className={`score ${verdict.className}`}>{effectiveScenario.riskScore}<small>/100</small></div></div><ul>{effectiveScenario.riskFactors.map((factor) => <li key={factor}><span><Check size={13} /></span>{factor}</li>)}</ul></section>
        <section className="card action-card"><p className="eyebrow">RECOMMENDED ACTION</p><h2>{effectiveScenario.verdict === "PASS" ? "Approve this decision" : effectiveScenario.verdict === "WARNING" ? "Review before ordering" : "Do not execute"}</h2><p className="muted">If gives the operator a clear next step instead of silently replacing the customer's AI.</p><button className="secondary-button" onClick={() => setError("A full audit-trail endpoint is not available yet. The latest monitoring result is shown above.")}>View full audit trail <ArrowUpRight size={16} /></button></section>
      </div>
      {error && <p className="error-message" role="alert">{error}</p>}
      <footer>IF / INVENTORY ASSURANCE <span>Turku SQLite data · Backend integration through services/api.ts</span></footer>
    </main>
  );
}

function SalesBar({ point, max }: { point: SalesPoint; max: number }) {
  const label = point.kind === "forecast" ? point.date.slice(5) : point.date.slice(5);
  return <div className={`bar-group ${point.kind === "forecast" ? "forecast-bar" : ""}`} title={`${point.date}: ${point.value} units (${point.kind})`}><div className="bar" style={{ height: `${Math.max(20, (point.value / max) * 100)}%` }} /><span>{label}</span></div>;
}

function Metric({ label, value, suffix, detail, tone = "default" }: { label: string; value: string; suffix: string; detail?: string; tone?: string }) { return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}<small>{suffix}</small></strong>{detail && <em>{detail}</em>}</div>; }
function DataRow({ label, value }: { label: string; value: string }) { return <div className="data-row"><span>{label}</span><strong>{value}</strong></div>; }
function MiniStat({ label, value }: { label: string; value: string }) { return <div><span>{label}</span><strong>{value}</strong></div>; }

export default App;
