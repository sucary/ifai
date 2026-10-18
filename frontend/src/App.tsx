import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, Check, ChevronDown, ShieldCheck, ShieldX } from "lucide-react";
import { getScenarios, runMonitoringCheck } from "./services/api";
import type { Judgment, Scenario, Verdict } from "./types";

const verdictCopy: Record<Verdict, { label: string; className: string }> = {
  PASS: { label: "Approved", className: "pass" },
  WARNING: { label: "Review required", className: "warning" },
  BLOCK: { label: "Blocked", className: "block" },
};

function App() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selectedId, setSelectedId] = useState("normal_day");
  const [status, setStatus] = useState<"idle" | "checking" | "complete">("idle");
  const [error, setError] = useState<string | null>(null);
  const [judgments, setJudgments] = useState<Judgment[]>([]);
  const [checkedDescriptionId, setCheckedDescriptionId] = useState<string | null>(null);
  const [riskOrders, setRiskOrders] = useState<Record<string, string[]>>({});
  const [draggedRisk, setDraggedRisk] = useState<{ scenarioId: string; factor: string } | null>(null);

  const runCheck = useCallback(async (descriptionId?: string) => {
    setStatus("checking");
    setError(null);
    try {
      setJudgments(await runMonitoringCheck());
      setStatus("complete");
      if (descriptionId) setCheckedDescriptionId(descriptionId);
    } catch (reason) {
      setStatus("idle");
      setError(reason instanceof Error ? reason.message : "Monitoring check failed.");
    }
  }, []);

  useEffect(() => {
    let active = true;

    const loadAndCheck = async () => {
      try {
        const loadedScenarios = await getScenarios();
        if (!active) return;
        setScenarios(loadedScenarios);
        await runCheck();
      } catch (reason) {
        if (active) setError(reason instanceof Error ? reason.message : "Could not load monitoring data.");
      }
    };

    void loadAndCheck();
    const interval = window.setInterval(() => void runCheck(), 60_000);

    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [runCheck]);

  const scenario = useMemo(
    () => scenarios.find((item) => item.id === selectedId) ?? scenarios[0],
    [scenarios, selectedId],
  );

  if (!scenario) return <main className="loading-screen">Loading database data…</main>;

  const judgment = judgments[scenarios.indexOf(scenario)];
  const effectiveScenario = judgment ? { ...scenario, verdict: judgment.verdict, riskScore: judgment.RiskScore, judgmentRiskFactor: judgment.riskFactor } : scenario;
  const verdict = verdictCopy[effectiveScenario.verdict];
  const savedOrder = riskOrders[scenario.id] ?? [];
  const warningFactors = effectiveScenario.riskFactors.filter((factor) => effectiveScenario.riskStatuses?.[factor] !== "pass");
  const orderedRisks = [
    ...savedOrder.filter((factor) => warningFactors.includes(factor)),
    ...warningFactors.filter((factor) => !savedOrder.includes(factor)),
  ];
  const moveRisk = (factor: string, target: string) => {
    if (factor === target) return;
    const reordered = orderedRisks.filter((item) => item !== factor);
    reordered.splice(reordered.indexOf(target), 0, factor);
    setRiskOrders((previous) => ({ ...previous, [scenario.id]: reordered }));
  };
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
            <select id="scenario" value={scenario.id} onChange={(event) => { setSelectedId(event.target.value); setCheckedDescriptionId(null); setJudgments([]); setStatus("idle"); setError(null); }}>
              {scenarios.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
            <ChevronDown size={16} />
          </div>
          <span>{scenario.description ?? scenario.date}</span>
        </div>
      </section>

      <section className="metric-grid">
        <Metric label="Current stock" value={`${scenario.currentStock}`} suffix="units" />
        <Metric label="Predicted sales" value={`${scenario.predictedSales}`} suffix="units" />
        <Metric label="AI recommended order" value={`${scenario.recommendedOrder}`} suffix="units" />
      </section>

      <div className="content-grid">
        <section className="card input-card">
          <div className="section-heading"><div><p className="eyebrow">01 / CONTEXT</p><h2>What If received</h2></div></div>
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
          <div className="section-heading"><div><p className="eyebrow">02 / EXTERNAL SYSTEM</p><h2>Customer AI decision</h2></div></div>
          <div className="ai-order"><span>Recommended order</span><strong>{scenario.recommendedOrder}<small> units</small></strong></div>
          <p className="muted">“{scenario.aiReasoning}”</p>
          <div className="mini-stats"><MiniStat label="Expected waste" value={`${scenario.predictedWaste} units`} /><MiniStat label="Stockout risk" value={`${scenario.stockoutRisk}%`} /><MiniStat label="Expiration risk" value={`${scenario.expirationRisk}%`} /></div>
          <button className="primary-button" onClick={() => void runCheck(scenario.id)} disabled={status === "checking"}>{status === "checking" ? "Checking decision…" : status === "complete" ? "Check complete" : "Run monitoring check"}</button>
        </section>
      </div>

      {checkedDescriptionId === scenario.id && <div className="monitoring-grid">



        <section className={`card risk-card ${verdict.className}${orderedRisks.length === 0 ? " no-risk-items" : ""}`}>
      <section className="verdict-card">
        <div className="verdict-icon">{effectiveScenario.verdict === "PASS" ? <ShieldCheck /> : effectiveScenario.verdict === "WARNING" ? <AlertTriangle /> : <ShieldX />}</div>
        <div className="verdict-main"><p className="eyebrow">03 / IF MONITORING VERDICT</p><div className="verdict-title"><h2>{effectiveScenario.verdict}</h2><div className={`score ${verdict.className}`}>{effectiveScenario.riskScore}<small>/100</small></div></div></div>
      </section>
          {checkedDescriptionId === scenario.id && orderedRisks.length > 0 && <>
<div className="section-heading"><div><p className="eyebrow">RISK EXPLANATION</p><h2>What went wrong?</h2></div></div>
          <ul>{orderedRisks.map((factor, index) => (
            <li key={factor} draggable tabIndex={0}
              className={draggedRisk?.scenarioId === scenario.id && draggedRisk.factor === factor ? "risk-dragging" : undefined}
              aria-label={`${factor} Use arrow keys to reorder.`}
              onDragStart={(event) => {
                event.dataTransfer.effectAllowed = "move";
                event.dataTransfer.setData("text/plain", factor);
                setDraggedRisk({ scenarioId: scenario.id, factor });
              }}
              onDragOver={(event) => {
                if (draggedRisk?.scenarioId === scenario.id) {
                  event.preventDefault();
                  event.dataTransfer.dropEffect = "move";
                }
              }}
              onDrop={(event) => {
                event.preventDefault();
                if (draggedRisk?.scenarioId === scenario.id) moveRisk(draggedRisk.factor, factor);
                setDraggedRisk(null);
              }}
              onDragEnd={() => setDraggedRisk(null)}
              onKeyDown={(event) => {
                const offset = event.key === "ArrowLeft" || event.key === "ArrowUp" ? -1 : event.key === "ArrowRight" || event.key === "ArrowDown" ? 1 : 0;
                if (!offset) return;
                event.preventDefault();
                const target = orderedRisks[index + offset];
                if (target) moveRisk(factor, target);
              }}
            ><span className={`risk-status-${effectiveScenario.riskStatuses?.[factor] ?? "warning"}`} role="img" aria-label={effectiveScenario.riskStatuses?.[factor] === "pass" ? "Passed" : "Warning"}>{effectiveScenario.riskStatuses?.[factor] === "pass" ? <Check size={13} aria-hidden="true" /> : <b aria-hidden="true">!</b>}</span>{factor}</li>
          ))}</ul>
          </>}
        </section>
      </div>
      }
      {error && <p className="error-message" role="alert">{error}</p>}
      <footer>IF / INVENTORY ASSURANCE <span>Turku SQLite data · Backend integration through services/api.ts</span></footer>
    </main>
  );
}

function Metric({ label, value, suffix, detail, tone = "default" }: { label: string; value: string; suffix: string; detail?: string; tone?: string }) { return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}<small>{suffix}</small></strong>{detail && <em>{detail}</em>}</div>; }
function DataRow({ label, value }: { label: string; value: string }) { return <div className="data-row"><span>{label}</span><strong>{value}</strong></div>; }
function MiniStat({ label, value }: { label: string; value: string }) { return <div><span>{label}</span><strong>{value}</strong></div>; }

export default App;
