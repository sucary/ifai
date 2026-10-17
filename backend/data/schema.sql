-- Grill-sausage inventory / customer-AI / If monitoring demo.
-- SQLite 3.37+; schema only: no seed data, forecasts or fabricated outcomes.
-- Execute on an empty database. Enable foreign_keys on EVERY connection.
-- Example: sqlite3 backend/data/demo.sqlite ".read backend/data/schema.sql"
-- One configured store; any number of products, including the grill-sausage SKU.
-- UTC timestamps: YYYY-MM-DDTHH:MM:SSZ. Business dates: YYYY-MM-DD in
-- app_config.timezone. Convert local expiry/cutoff times in application code.
-- Money uses INTEGER euro cents; quantities use the product's retail unit.
-- VAT/discount rates use basis points (1350 = 13.5%). No hard-coded tax rate.
-- Application validates timestamp/date format, overlapping effective periods,
-- source availability at input cutoff, and cross-table aggregate reconciliation.
-- It also validates prediction horizons, scoring/verdict rules, and order/batch
-- product alignment. SQL structural checks do not replace the monitoring logic.
-- Populate actual/history tables only for recorded past events. Synthetic demo
-- history is allowed only with an explicitly simulated data_source.

PRAGMA foreign_keys = ON;
BEGIN TRANSACTION;

CREATE TABLE data_sources (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN
        ('observed', 'published', 'forecast', 'model_estimate', 'simulated', 'configured')),
    uri TEXT,
    retrieved_at TEXT NOT NULL,
    notes TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(metadata_json))
) STRICT;

CREATE TABLE app_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    store_name TEXT NOT NULL,
    location TEXT NOT NULL,
    latitude REAL CHECK (latitude BETWEEN -90 AND 90),
    longitude REAL CHECK (longitude BETWEEN -180 AND 180),
    timezone TEXT NOT NULL, -- e.g. Europe/Helsinki
    currency TEXT NOT NULL DEFAULT 'EUR' CHECK (currency = 'EUR'),
    daily_order_budget_ex_vat_cents INTEGER NOT NULL
        CHECK (daily_order_budget_ex_vat_cents >= 0),
    source_id INTEGER NOT NULL REFERENCES data_sources(id)
) STRICT;

CREATE TABLE products (
    id INTEGER PRIMARY KEY,
    sku TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    retail_unit TEXT NOT NULL, -- e.g. 400 g grill-sausage pack
    storage_capacity_units INTEGER NOT NULL CHECK (storage_capacity_units >= 0),
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    source_id INTEGER NOT NULL REFERENCES data_sources(id)
) STRICT;

CREATE TABLE product_prices (
    id INTEGER PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id),
    valid_from TEXT NOT NULL,
    valid_until TEXT,
    selling_price_incl_vat_cents INTEGER NOT NULL CHECK (selling_price_incl_vat_cents >= 0),
    vat_basis_points INTEGER NOT NULL CHECK (vat_basis_points BETWEEN 0 AND 10000),
    known_at TEXT NOT NULL,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    UNIQUE (product_id, valid_from),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
) STRICT;

CREATE TABLE suppliers (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    order_cutoff_local TEXT NOT NULL, -- HH:MM in store timezone
    lead_time_calendar_days INTEGER NOT NULL CHECK (lead_time_calendar_days >= 0),
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    source_id INTEGER NOT NULL REFERENCES data_sources(id)
) STRICT;

CREATE TABLE supplier_products (
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    product_id INTEGER NOT NULL REFERENCES products(id),
    purchase_cost_ex_vat_cents INTEGER NOT NULL CHECK (purchase_cost_ex_vat_cents >= 0),
    pack_size_units INTEGER NOT NULL CHECK (pack_size_units > 0),
    minimum_order_units INTEGER NOT NULL CHECK (minimum_order_units > 0),
    maximum_order_units INTEGER NOT NULL,
    saleable_days_on_arrival INTEGER NOT NULL CHECK (saleable_days_on_arrival > 0),
    valid_from TEXT NOT NULL,
    valid_until TEXT,
    known_at TEXT NOT NULL,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    PRIMARY KEY (supplier_id, product_id, valid_from),
    CHECK (maximum_order_units >= minimum_order_units),
    CHECK (minimum_order_units % pack_size_units = 0),
    CHECK (maximum_order_units % pack_size_units = 0),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
) STRICT;

CREATE TABLE supplier_delivery_days (
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    weekday INTEGER NOT NULL CHECK (weekday BETWEEN 0 AND 6), -- Monday=0
    delivery_time_local TEXT NOT NULL,
    PRIMARY KEY (supplier_id, weekday)
) STRICT;

-- Announced future availability/capacity is a known constraint, not an outcome.
-- Missing date/capacity means unknown; do not silently treat it as available.
CREATE TABLE supplier_availability (
    id INTEGER PRIMARY KEY,
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    delivery_date TEXT NOT NULL,
    available INTEGER NOT NULL CHECK (available IN (0, 1)),
    capacity_units INTEGER CHECK (capacity_units >= 0), -- aggregate supplier/day
    known_at TEXT NOT NULL,
    reason TEXT,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    UNIQUE (supplier_id, delivery_date, known_at),
    CHECK (available = 1 OR capacity_units IS NULL OR capacity_units = 0)
) STRICT;

CREATE TABLE weather_observations (
    id INTEGER PRIMARY KEY,
    business_date TEXT NOT NULL,
    location TEXT NOT NULL,
    period_start TEXT NOT NULL,
    period_end TEXT NOT NULL,
    day_basis TEXT NOT NULL, -- retain station/native periods vs local day
    temperature_mean_c REAL,
    temperature_min_c REAL,
    temperature_max_c REAL,
    precipitation_mm REAL CHECK (precipitation_mm >= 0),
    available_at TEXT NOT NULL,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    CHECK (period_end > period_start),
    CHECK (available_at >= period_end),
    CHECK (temperature_min_c IS NULL OR temperature_max_c IS NULL
           OR temperature_min_c <= temperature_max_c),
    CHECK (temperature_mean_c IS NOT NULL OR precipitation_mm IS NOT NULL)
) STRICT;

-- Keep forecast versions; never overwrite them with later observed weather.
CREATE TABLE weather_forecasts (
    id INTEGER PRIMARY KEY,
    target_date TEXT NOT NULL,
    location TEXT NOT NULL,
    analysis_at TEXT,
    issued_at TEXT NOT NULL,
    available_at TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN
        ('forecast', 'forecast_with_seasonal_completion', 'seasonal_outlook')),
    temperature_mean_c REAL,
    precipitation_expected_mm REAL CHECK (precipitation_expected_mm >= 0),
    rain_probability REAL CHECK (rain_probability BETWEEN 0 AND 1),
    temperature_uncertainty_c REAL CHECK (temperature_uncertainty_c >= 0),
    coverage_hours INTEGER CHECK (coverage_hours BETWEEN 0 AND 25),
    day_hours INTEGER NOT NULL CHECK (day_hours IN (23, 24, 25)),
    uncertainty_basis TEXT, -- issued ensemble value or explicit assumption
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    UNIQUE (source_id, location, target_date, issued_at),
    CHECK (analysis_at IS NULL OR analysis_at <= issued_at),
    CHECK (available_at >= issued_at),
    CHECK (coverage_hours IS NULL OR coverage_hours <= day_hours)
) STRICT;

CREATE TABLE events (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    location TEXT NOT NULL,
    starts_at TEXT NOT NULL,
    ends_at TEXT NOT NULL,
    known_at TEXT NOT NULL,
    description TEXT,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    CHECK (ends_at > starts_at)
) STRICT;

CREATE TABLE promotions (
    id INTEGER PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id),
    name TEXT NOT NULL,
    starts_at TEXT NOT NULL,
    ends_at TEXT NOT NULL,
    known_at TEXT NOT NULL,
    promotional_price_incl_vat_cents INTEGER
        CHECK (promotional_price_incl_vat_cents >= 0),
    discount_basis_points INTEGER CHECK (discount_basis_points BETWEEN 0 AND 10000),
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    CHECK (ends_at > starts_at),
    CHECK ((promotional_price_incl_vat_cents IS NOT NULL)
           <> (discount_basis_points IS NOT NULL))
) STRICT;

-- Version rules instead of changing the rules used by earlier assessments.
CREATE TABLE business_rules (
    id INTEGER PRIMARY KEY,
    rule_key TEXT NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    description TEXT NOT NULL,
    enforcement TEXT NOT NULL CHECK (enforcement IN ('advisory', 'mandatory')),
    parameters_json TEXT NOT NULL CHECK (json_valid(parameters_json)),
    effective_from TEXT NOT NULL,
    effective_until TEXT,
    known_at TEXT NOT NULL,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    UNIQUE (rule_key, version),
    CHECK (effective_until IS NULL OR effective_until > effective_from)
) STRICT;

CREATE TABLE ai_runs (
    id INTEGER PRIMARY KEY,
    requested_at TEXT NOT NULL,
    input_cutoff_at TEXT NOT NULL,
    target_start_date TEXT NOT NULL,
    target_end_date TEXT NOT NULL,
    model TEXT NOT NULL,
    system_prompt TEXT NOT NULL,
    model_parameters_json TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(model_parameters_json)),
    input_snapshot_json TEXT NOT NULL CHECK (json_valid(input_snapshot_json)),
    response_json TEXT CHECK (response_json IS NULL OR json_valid(response_json)),
    status TEXT NOT NULL CHECK (status IN ('pending', 'succeeded', 'failed')),
    completed_at TEXT,
    error_message TEXT,
    CHECK (input_cutoff_at <= requested_at),
    CHECK (target_end_date >= target_start_date),
    CHECK (completed_at IS NULL OR completed_at >= requested_at),
    CHECK (status <> 'succeeded' OR (response_json IS NOT NULL AND completed_at IS NOT NULL)),
    CHECK (status <> 'failed' OR (error_message IS NOT NULL AND completed_at IS NOT NULL)),
    CHECK (status <> 'pending' OR completed_at IS NULL)
) STRICT;

-- Predictions are estimates produced by the customer's AI, never actuals.
CREATE TABLE ai_predictions (
    id INTEGER PRIMARY KEY,
    run_id INTEGER NOT NULL REFERENCES ai_runs(id),
    product_id INTEGER NOT NULL REFERENCES products(id),
    target_date TEXT NOT NULL,
    predicted_sales_units REAL NOT NULL CHECK (predicted_sales_units >= 0),
    predicted_expired_units REAL NOT NULL CHECK (predicted_expired_units >= 0),
    predicted_waste_cost_ex_vat_cents INTEGER CHECK (predicted_waste_cost_ex_vat_cents >= 0),
    stockout_probability REAL CHECK (stockout_probability BETWEEN 0 AND 1),
    expiration_risk_probability REAL CHECK (expiration_risk_probability BETWEEN 0 AND 1),
    explanation TEXT,
    UNIQUE (run_id, product_id, target_date)
) STRICT;

-- Keep proposed quantities even if they violate supplier/budget rules so If can
-- explain/block them. Raw malformed/negative AI outputs remain in response_json.
CREATE TABLE restock_decisions (
    id INTEGER PRIMARY KEY,
    run_id INTEGER NOT NULL REFERENCES ai_runs(id),
    product_id INTEGER NOT NULL REFERENCES products(id),
    supplier_id INTEGER REFERENCES suppliers(id), -- NULL for no restock
    quantity_units INTEGER NOT NULL CHECK (quantity_units >= 0),
    proposed_submit_at TEXT,
    proposed_arrival_at TEXT,
    proposed_unit_cost_ex_vat_cents INTEGER CHECK (proposed_unit_cost_ex_vat_cents >= 0),
    explanation TEXT,
    UNIQUE (id, run_id),
    CHECK (quantity_units = 0 OR supplier_id IS NOT NULL)
) STRICT;

-- Scoring weights/aggregation live in the versioned policy, not guessed defaults.
CREATE TABLE monitoring_policies (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    warning_threshold REAL NOT NULL CHECK (warning_threshold BETWEEN 0 AND 100),
    block_threshold REAL NOT NULL CHECK (block_threshold BETWEEN 0 AND 100),
    scoring_rules_json TEXT NOT NULL CHECK (json_valid(scoring_rules_json)),
    created_at TEXT NOT NULL,
    UNIQUE (name, version),
    CHECK (warning_threshold < block_threshold)
) STRICT;

CREATE TABLE monitoring_assessments (
    id INTEGER PRIMARY KEY,
    run_id INTEGER NOT NULL REFERENCES ai_runs(id),
    decision_id INTEGER,
    policy_id INTEGER NOT NULL REFERENCES monitoring_policies(id),
    assessed_at TEXT NOT NULL,
    risk_score REAL NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    verdict TEXT NOT NULL CHECK (verdict IN ('pass', 'warning', 'block')),
    rules_snapshot_json TEXT NOT NULL CHECK (json_valid(rules_snapshot_json)),
    previous_performance_snapshot_json TEXT NOT NULL DEFAULT '{}'
        CHECK (json_valid(previous_performance_snapshot_json)),
    summary TEXT NOT NULL,
    FOREIGN KEY (decision_id, run_id) REFERENCES restock_decisions(id, run_id)
) STRICT;

CREATE TABLE risk_factors (
    id INTEGER PRIMARY KEY,
    assessment_id INTEGER NOT NULL REFERENCES monitoring_assessments(id),
    business_rule_id INTEGER REFERENCES business_rules(id),
    code TEXT NOT NULL, -- e.g. DELIVERY_TOO_LATE, EXPIRY_RISK, BUDGET_EXCEEDED
    severity TEXT NOT NULL CHECK (severity IN ('info', 'warning', 'block')),
    score_contribution REAL NOT NULL CHECK (score_contribution BETWEEN -100 AND 100),
    explanation TEXT NOT NULL,
    evidence_json TEXT NOT NULL CHECK (json_valid(evidence_json))
) STRICT;

-- A proposal is NOT a purchase order. Create orders only after the application's
-- monitoring/approval step. Partial receipts are separate recorded movements.
CREATE TABLE purchase_orders (
    id INTEGER PRIMARY KEY,
    decision_id INTEGER REFERENCES restock_decisions(id),
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    product_id INTEGER NOT NULL REFERENCES products(id),
    created_at TEXT NOT NULL,
    submitted_at TEXT,
    expected_arrival_at TEXT,
    quantity_units INTEGER NOT NULL CHECK (quantity_units > 0),
    unit_cost_ex_vat_cents INTEGER NOT NULL CHECK (unit_cost_ex_vat_cents >= 0),
    status TEXT NOT NULL CHECK (status IN ('planned', 'submitted', 'confirmed', 'closed', 'cancelled')),
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    CHECK (submitted_at IS NULL OR submitted_at >= created_at),
    CHECK (status NOT IN ('submitted', 'confirmed', 'closed') OR submitted_at IS NOT NULL),
    CHECK (status <> 'planned' OR submitted_at IS NULL)
) STRICT;

CREATE TABLE inventory_batches (
    id INTEGER PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id),
    purchase_order_id INTEGER REFERENCES purchase_orders(id), -- NULL for opening stock
    received_at TEXT NOT NULL,
    remove_at TEXT NOT NULL, -- exact UTC instant corresponding to local expiry
    unit_cost_ex_vat_cents INTEGER NOT NULL CHECK (unit_cost_ex_vat_cents >= 0),
    recorded_at TEXT NOT NULL,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    CHECK (remove_at > received_at),
    CHECK (recorded_at >= received_at)
) STRICT;

-- A batch's opening quantity is its receipt movement; do not add it twice.
-- Only recorded receipts/sales/losses go here. Future expected receipts stay in
-- purchase_orders. Application enforces nonnegative batch balance and FEFO.
CREATE TABLE stock_movements (
    id INTEGER PRIMARY KEY,
    batch_id INTEGER NOT NULL REFERENCES inventory_batches(id),
    occurred_at TEXT NOT NULL,
    recorded_at TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('receipt', 'sale', 'expired', 'damaged', 'adjustment')),
    quantity_delta INTEGER NOT NULL CHECK (quantity_delta <> 0),
    reason TEXT,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    CHECK (recorded_at >= occurred_at),
    CHECK ((kind = 'receipt' AND quantity_delta > 0)
        OR (kind IN ('sale', 'expired', 'damaged') AND quantity_delta < 0)
        OR kind = 'adjustment')
) STRICT;
CREATE UNIQUE INDEX one_receipt_per_batch ON stock_movements(batch_id) WHERE kind = 'receipt';

-- Completed daily summaries, reconciled with batch movements by application.
-- NULL unmet demand/stockout means unmeasured, not zero demand or no stockout.
CREATE TABLE daily_actuals (
    id INTEGER PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(id),
    business_date TEXT NOT NULL,
    opening_units INTEGER NOT NULL CHECK (opening_units >= 0),
    delivered_units INTEGER NOT NULL CHECK (delivered_units >= 0),
    sold_units INTEGER NOT NULL CHECK (sold_units >= 0),
    expired_units INTEGER NOT NULL CHECK (expired_units >= 0),
    damaged_units INTEGER NOT NULL DEFAULT 0 CHECK (damaged_units >= 0),
    adjustment_units INTEGER NOT NULL DEFAULT 0,
    closing_units INTEGER NOT NULL CHECK (closing_units >= 0),
    revenue_incl_vat_cents INTEGER NOT NULL CHECK (revenue_incl_vat_cents >= 0),
    waste_cost_ex_vat_cents INTEGER NOT NULL CHECK (waste_cost_ex_vat_cents >= 0),
    unmet_demand_units INTEGER CHECK (unmet_demand_units >= 0),
    stockout_observed INTEGER CHECK (stockout_observed IN (0, 1)),
    available_at TEXT NOT NULL,
    source_id INTEGER NOT NULL REFERENCES data_sources(id),
    UNIQUE (product_id, business_date),
    CHECK (opening_units + delivered_units + adjustment_units
           - sold_units - expired_units - damaged_units = closing_units)
) STRICT;

-- Created only once outcomes are recorded. Accuracy cannot be scored in advance.
CREATE TABLE prediction_evaluations (
    id INTEGER PRIMARY KEY,
    prediction_id INTEGER NOT NULL UNIQUE REFERENCES ai_predictions(id),
    daily_actual_id INTEGER NOT NULL REFERENCES daily_actuals(id),
    evaluated_at TEXT NOT NULL,
    evaluator_version TEXT NOT NULL,
    notes TEXT
) STRICT;

CREATE INDEX prices_product_period ON product_prices(product_id, valid_from, valid_until);
CREATE INDEX availability_supplier_date ON supplier_availability(supplier_id, delivery_date, known_at);
CREATE INDEX observations_date ON weather_observations(business_date, available_at);
CREATE INDEX forecasts_target_issue ON weather_forecasts(target_date, issued_at);
CREATE INDEX promotions_product_period ON promotions(product_id, starts_at, ends_at);
CREATE INDEX predictions_product_date ON ai_predictions(product_id, target_date);
CREATE INDEX assessments_run_time ON monitoring_assessments(run_id, assessed_at);
CREATE INDEX orders_status_arrival ON purchase_orders(status, expected_arrival_at);
CREATE INDEX batches_product_expiry ON inventory_batches(product_id, remove_at);
CREATE INDEX movements_batch_time ON stock_movements(batch_id, occurred_at);

-- Dashboard/query helpers contain recorded balances, not predicted outcomes.
CREATE VIEW batch_balances AS
SELECT b.id AS batch_id, b.product_id, b.received_at, b.remove_at,
       b.unit_cost_ex_vat_cents, COALESCE(SUM(m.quantity_delta), 0) AS remaining_units
FROM inventory_batches b LEFT JOIN stock_movements m ON m.batch_id = b.id
GROUP BY b.id;

CREATE VIEW product_stock AS
SELECT p.id AS product_id, p.sku, p.name,
       COALESCE(SUM(b.remaining_units), 0) AS recorded_on_hand_units,
       COALESCE(SUM(CASE WHEN b.remove_at > strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                         THEN b.remaining_units ELSE 0 END), 0) AS saleable_now_units
FROM products p LEFT JOIN batch_balances b ON b.product_id = p.id
GROUP BY p.id;

CREATE VIEW pending_orders AS
SELECT o.*, o.quantity_units * o.unit_cost_ex_vat_cents AS ordered_cost_ex_vat_cents,
       COALESCE((SELECT SUM(m.quantity_delta)
                 FROM inventory_batches b JOIN stock_movements m ON m.batch_id = b.id
                 WHERE b.purchase_order_id = o.id AND m.kind = 'receipt'), 0) AS received_units
FROM purchase_orders o WHERE o.status IN ('planned', 'submitted', 'confirmed');

CREATE VIEW ai_prediction_performance AS
SELECT e.id AS evaluation_id, p.run_id, p.product_id, p.target_date,
       e.evaluated_at, e.evaluator_version, s.kind AS actual_data_kind,
       p.predicted_sales_units, a.sold_units AS actual_sales_units,
       ABS(p.predicted_sales_units - a.sold_units) AS sales_absolute_error_units,
       p.predicted_expired_units, a.expired_units AS actual_expired_units,
       ABS(p.predicted_expired_units - a.expired_units) AS expiry_absolute_error_units,
       CASE WHEN a.stockout_observed IS NOT NULL AND p.stockout_probability IS NOT NULL
            THEN (p.stockout_probability - a.stockout_observed)
               * (p.stockout_probability - a.stockout_observed) END AS stockout_brier_score
FROM prediction_evaluations e JOIN ai_predictions p ON p.id = e.prediction_id
JOIN daily_actuals a ON a.id = e.daily_actual_id AND a.product_id = p.product_id
                     AND a.business_date = p.target_date
JOIN data_sources s ON s.id = a.source_id;

-- Guard evaluation alignment/availability at the database boundary.
CREATE TRIGGER validate_prediction_evaluation_insert
BEFORE INSERT ON prediction_evaluations
WHEN NOT EXISTS (
    SELECT 1 FROM ai_predictions p JOIN ai_runs r ON r.id = p.run_id JOIN daily_actuals a
      ON a.product_id = p.product_id AND a.business_date = p.target_date
    WHERE p.id = NEW.prediction_id AND a.id = NEW.daily_actual_id
      AND a.available_at <= NEW.evaluated_at
      AND r.status = 'succeeded' AND r.completed_at <= a.available_at
      AND r.completed_at <= NEW.evaluated_at
)
BEGIN
    SELECT RAISE(ABORT, 'Evaluation requires matching, already available actuals');
END;

CREATE TRIGGER validate_prediction_evaluation_update
BEFORE UPDATE ON prediction_evaluations
WHEN NOT EXISTS (
    SELECT 1 FROM ai_predictions p JOIN ai_runs r ON r.id = p.run_id JOIN daily_actuals a
      ON a.product_id = p.product_id AND a.business_date = p.target_date
    WHERE p.id = NEW.prediction_id AND a.id = NEW.daily_actual_id
      AND a.available_at <= NEW.evaluated_at
      AND r.status = 'succeeded' AND r.completed_at <= a.available_at
      AND r.completed_at <= NEW.evaluated_at
)
BEGIN
    SELECT RAISE(ABORT, 'Evaluation requires matching, already available actuals');
END;

COMMIT;
