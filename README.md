# If AI Inventory Monitoring

If AI is a prototype that checks inventory recommendations against Turku business data before an order is acted on.

## How it works

1. `backend/data/turku_data.sqlite` stores products, sales history, inventory, suppliers, calendar events, and weather data.
2. `TurkuDatabaseService` reads that data and builds current-stock, forecast, and historical-sales information.
3. `CustomerAiService` produces a rule-based purchasing recommendation.
4. The monitoring judgment checks the recommendation for stockout and expiration risk.
5. The React frontend displays the scenario, recommendation, historical-vs-forecast chart, and monitoring result.

The system is read-only: it observes and reports. It does not place, approve, or modify orders.

## API

```text
GET  /api/scenarios
POST /api/judgment/run
POST /api/customer-ai/analyze
```

## Run locally

Start the backend from `backend/`:

```powershell
.\gradlew.bat bootRun
```

Start the frontend from `frontend/`:

```powershell
npm.cmd run dev
```

Open `http://localhost:5173`. The frontend proxies API requests to `http://localhost:8080`.
