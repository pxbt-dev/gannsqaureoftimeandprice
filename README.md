# Gann Square of Time & Price

A Spring Boot web app that applies W.D. Gann analysis to cryptocurrency price data fetched from the Binance public API.

## What it does

Given a trading pair and date range, it:

1. Fetches OHLCV candlestick data from Binance (paginated, cached to disk)
2. Finds the period high and low
3. Calculates four types of projections:
   - **Time projections** — Gann (1/8 divisions) and Fibonacci ratios applied to the high-to-low day count, projected forward from the high
   - **Price projections** — retracement levels (Gann + Fibonacci) from high to low, plus extension levels (127.2%, 161.8%, 200%, 261.8%) projected above the high
   - **Gann angle projections** — seven angles (8×1, 4×1, 2×1, 1×1, 1×2, 1×4, 1×8) projected forward from the period low at 30/60/90/120/180/360-day intervals
   - **Square of Nine levels** — support/resistance prices calculated by taking √(midprice), adding/subtracting increments of 0.25 (= 45°), and squaring back

The frontend renders these as a price chart with overlaid Gann levels, four projection tables, and an interactive Square of Nine spiral grid.

## Stack

- Java 21, Spring Boot 3.2.4
- No external JSON library — hand-rolled parsing throughout
- Disk cache (`./data/SYMBOL_INTERVAL.json`) avoids re-fetching data on repeat requests
- Vanilla JS/HTML/CSS frontend (no framework, Canvas-based chart and grid)

## API endpoints

| Endpoint | Params | Description |
|----------|--------|-------------|
| `GET /api/gann` | `symbol`, `interval`, `limit`, `startDate` | Full Gann analysis as JSON |
| `GET /api/klines` | `symbol`, `interval`, `limit` | Raw Binance klines proxy |

Default symbol is `BTC`, default interval is `1d`, default limit is `365`.  
`startDate` accepts `YYYY-MM-DD`. When provided, data is paginated from that date to today (bypassing the 1000-candle per-request Binance limit).

Example:
```
GET /api/gann?symbol=ETH&interval=1d&startDate=2020-01-01
```

## Running locally

```bash
./mvnw package -DskipTests
java -jar target/*.jar
```

Then open `http://localhost:8080`.

## Deployment

Configured for [Railway](https://railway.app) via `railway.json`. The start command is `java -jar target/*.jar` and the `PORT` environment variable is respected via `server.port=${PORT:8080}`.

## Cache

Candle data is written to `./data/` as flat JSON arrays, one file per symbol/interval pair (e.g. `data/BTCUSDT_1d.json`). On subsequent requests, only the missing tail is fetched from Binance and merged in. The cache survives restarts.

## Limitations

- Only supports Binance USDT pairs (symbol is automatically suffixed with `USDT` if not already)
- Square of Nine grid is fixed at size 9 (81 cells)
- No authentication — Binance public API only, rate-limited with a 100ms sleep between pages
- `server.error.include-stacktrace=always` is on — not suitable for public exposure as-is
