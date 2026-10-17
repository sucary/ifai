# IF AI Inventory Assurance

IF is an independent monitoring layer for inventory decisions made by a customer's AI.

The customer's AI proposes an order. IF checks the proposal against business data and operational rules before anyone acts on it. The system reports risks and gives the operator guidance; it does not place or approve orders.

## Decision flow

```text
Customer inventory data
        |
        v
CustomerAiService
        |
        |  proposed inventory decision
        v
IF monitoring and judgment
        |
        v
PASS / WARNING / BLOCK + risk explanation
```

## Prototype architecture

### Customer AI

`CustomerAiService` is the customer's inventory AI in this prototype. It produces the single customer decision from:

- recent sales
- current stock
- expiring stock
- events and promotions
- supplier order limits
- delivery lead time

The repeatable cases are stored in [backend/src/main/resources/scenario.json](backend/src/main/resources/scenario.json). The `faulty_over_order` case intentionally returns an order above the supplier limit so IF can detect it.

### IF monitoring

`TurkuDatabaseService` reads Turku data from [backend/data/turku_data.sqlite](backend/data/turku_data.sqlite) and provides stock, sales, event, weather, supplier, and forecast context.

`JudgmentService` independently evaluates the customer's decision, calculates a risk score, identifies the main risk factor, and saves the monitoring result. Monitoring results use an embedded H2 database during the prototype run and are reset when the backend restarts.

### Frontend

The React dashboard loads the customer decision first. Selecting another scenario resets the previous monitoring result. Clicking **Run monitoring check** overlays IF's latest judgment for the selected set of scenarios.

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/customer-ai/analyze` | Evaluate one inventory input with the customer AI |
| `GET` | `/api/scenarios` | Load the repeatable customer-AI scenarios and decisions |
| `POST` | `/api/judgment/run` | Run IF monitoring against all scenarios and save the results |

## Requirements

- Java 21
- Node.js and npm
- Network access the first time Gradle or npm dependencies are downloaded

## Run locally

Start the backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Start the frontend in a second terminal:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open [http://localhost:5173](http://localhost:5173). The frontend proxies API requests to `http://localhost:8080`.

## Project structure

```text
backend/
  data/turku_data.sqlite          Turku inventory and business data
  src/main/java/.../customerai/   Customer AI, scenarios, and data context
  src/main/java/.../service/      IF monitoring judgment logic
  src/main/resources/scenario.json Repeatable demo scenarios
frontend/
  src/App.tsx                     Dashboard
  src/services/api.ts             Backend API client
```
