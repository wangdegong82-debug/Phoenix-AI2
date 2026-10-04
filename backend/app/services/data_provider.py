from __future__ import annotations

import os
from datetime import datetime, timezone
import httpx


class FootballDataProvider:
    async def today(self) -> dict:
        provider = os.getenv("FOOTBALL_DATA_PROVIDER", "mock").lower()
        if provider == "mock":
            return {
                "provider": "mock",
                "date": datetime.now(timezone.utc).date().isoformat(),
                "matches": [],
                "message": "Configure a licensed football data provider for live fixtures.",
            }

        base = os.getenv("FOOTBALL_DATA_BASE_URL", "").rstrip("/")
        key = os.getenv("FOOTBALL_DATA_API_KEY", "")
        if not base or not key:
            return {"provider": provider, "matches": [], "error": "Provider is not configured"}

        async with httpx.AsyncClient(timeout=15) as client:
            response = await client.get(
                f"{base}/fixtures",
                params={"date": datetime.now(timezone.utc).date().isoformat()},
                headers={"Authorization": f"Bearer {key}"},
            )
            response.raise_for_status()
            return {"provider": provider, "raw": response.json()}


football_data_provider = FootballDataProvider()
