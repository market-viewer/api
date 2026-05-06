# Market Viewer API

Backend REST API for the **Market Viewer** — a device that displays real-time market data (stocks, crypto, clock, AI text) on small microcontroller with display.

> ⚠️ Project is still work in progress

---

## Prerequisites

- Docker & Docker Compose
- A `.env` file in the project root (see template below)

---

## Environment Variables

Copy `.env.example` to `.env` and set the variables:

```
cp .env.docker .env
```
> Note: Update environment variables for public API deployments. Default values are safe for local use only.

> ⚠️ Note on Database Credentials:
If you change the database credentials in your .env file after the application has already been started, you must fully reset the database environment for the changes to take effect. You can do this by running `sudo docker compose down -v`.
Warning: This command removes the database volume and will permanently delete all your existing data.
 
---

## Running with Docker

1) Download the latest release or clone this repo
2) Run with docker

- run these commands (for linux):
```bash
git clone https://github.com/market-viewer/api.git &&
cd api &&
cp .env.example .env &&
docker compose up -d
```

The API will be available at `http://localhost:8080`.

---
### API docs

Full interactive docs available at `https://api.market-viewer.jotalac.dev/swagger-ui/index.html`.
- or `http://localhost:8080/swagger-ui/index.html` - when you run the app locally

---

## Supported data providers

- **Crypto**:
  - coingecko
- **Stock**
  - twelvedata
