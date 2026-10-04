from __future__ import annotations

import os
from datetime import datetime
from zoneinfo import ZoneInfo
from typing import Any

import httpx

from ..models import MatchInput


def _local_date() -> str:
    tz = ZoneInfo(os.getenv("PHOENIX_TIMEZONE", "Asia/Shanghai"))
    return datetime.now(tz).date().isoformat()


def _safe_name(value: Any, fallback: str = "") -> str:
    return str(value or fallback)


class FootballDataProvider:
    def __init__(self) -> None:
        self.provider = os.getenv("FOOTBALL_DATA_PROVIDER", "demo").lower().strip()
        self.base_url = os.getenv(
            "FOOTBALL_DATA_BASE_URL",
            "https://v3.football.api-sports.io",
        ).rstrip("/")
        self.api_key = os.getenv("FOOTBALL_DATA_API_KEY", "").strip()
        self.timezone = os.getenv("PHOENIX_TIMEZONE", "Asia/Shanghai")

    async def today(self) -> dict:
        if self.provider in {"demo", "mock"} or not self.api_key:
            return self._demo_today()
        if self.provider in {"api-football", "api_football", "apisports"}:
            return await self._api_football_today()
        return {
            "provider": self.provider,
            "date": _local_date(),
            "matches": [],
            "message": "Unsupported provider. Use demo or api-football.",
        }

    async def build_match_input(self, match_id: str) -> tuple[MatchInput, dict]:
        if self.provider in {"demo", "mock"} or not self.api_key:
            return self._demo_match(match_id)
        if self.provider in {"api-football", "api_football", "apisports"}:
            return await self._api_football_match(match_id)
        raise ValueError("Unsupported football data provider")

    def _demo_today(self) -> dict:
        date = _local_date()
        return {
            "provider": "demo",
            "date": date,
            "matches": [
                {
                    "match_id": "demo-001",
                    "home": "Phoenix Red",
                    "away": "Aurora Blue",
                    "kickoff": f"{date}T19:30:00+08:00",
                    "competition": "Phoenix Demo League",
                    "status": "NS",
                    "venue": "Demo Stadium",
                },
                {
                    "match_id": "demo-002",
                    "home": "Kunming United",
                    "away": "Cloud City FC",
                    "kickoff": f"{date}T21:00:00+08:00",
                    "competition": "Phoenix Demo League",
                    "status": "NS",
                    "venue": "Demo Arena",
                },
            ],
            "message": (
                "Demo mode. Set FOOTBALL_DATA_PROVIDER=api-football and "
                "FOOTBALL_DATA_API_KEY on the backend for real fixtures."
            ),
        }

    def _demo_match(self, match_id: str) -> tuple[MatchInput, dict]:
        if match_id == "demo-002":
            item = MatchInput(
                match_id=match_id,
                home="Kunming United",
                away="Cloud City FC",
                competition="Phoenix Demo League",
                home_form=0.61,
                away_form=0.58,
                h2h_home_edge=-0.05,
                squad_home=0.88,
                squad_away=0.92,
                context_home=0.58,
                context_away=0.42,
                market_home=0.44,
                market_draw=0.29,
                market_away=0.27,
                notes=["演示数据：真实模式会替换为 API-Football 实时数据"],
            )
        else:
            item = MatchInput(
                match_id=match_id,
                home="Phoenix Red",
                away="Aurora Blue",
                competition="Phoenix Demo League",
                home_form=0.72,
                away_form=0.47,
                h2h_home_edge=0.18,
                squad_home=0.93,
                squad_away=0.82,
                context_home=0.59,
                context_away=0.41,
                market_home=0.56,
                market_draw=0.25,
                market_away=0.19,
                notes=["演示数据：用于验证 App / API / 模型链路"],
            )
        evidence = {
            "provider": "demo",
            "mode": "演示",
            "warning": "不是实时比赛数据",
        }
        return item, evidence

    async def _get(self, path: str, params: dict[str, Any]) -> dict:
        headers = {"x-apisports-key": self.api_key}
        async with httpx.AsyncClient(timeout=18) as client:
            response = await client.get(
                f"{self.base_url}{path}",
                params=params,
                headers=headers,
            )
            response.raise_for_status()
            payload = response.json()

        errors = payload.get("errors")
        if errors:
            raise ValueError(f"API-Football error: {errors}")
        return payload

    async def _api_football_today(self) -> dict:
        payload = await self._get(
            "/fixtures",
            {"date": _local_date(), "timezone": self.timezone},
        )
        rows = []
        for item in payload.get("response", []):
            fixture = item.get("fixture", {})
            league = item.get("league", {})
            teams = item.get("teams", {})
            venue = fixture.get("venue") or {}
            status = fixture.get("status") or {}
            rows.append(
                {
                    "match_id": str(fixture.get("id", "")),
                    "home": _safe_name((teams.get("home") or {}).get("name")),
                    "away": _safe_name((teams.get("away") or {}).get("name")),
                    "kickoff": _safe_name(fixture.get("date")),
                    "competition": _safe_name(league.get("name")),
                    "status": _safe_name(status.get("short")),
                    "venue": _safe_name(venue.get("name")),
                }
            )
        return {
            "provider": "api-football",
            "date": _local_date(),
            "matches": rows,
            "count": len(rows),
        }

    async def _api_football_match(self, match_id: str) -> tuple[MatchInput, dict]:
        fixture_payload = await self._get(
            "/fixtures",
            {"id": match_id, "timezone": self.timezone},
        )
        responses = fixture_payload.get("response", [])
        if not responses:
            raise ValueError("Match not found")

        item = responses[0]
        fixture = item.get("fixture", {})
        league = item.get("league", {})
        teams = item.get("teams", {})
        home_team = teams.get("home") or {}
        away_team = teams.get("away") or {}
        home_id = int(home_team.get("id"))
        away_id = int(away_team.get("id"))

        home_form_payload = await self._get("/fixtures", {"team": home_id, "last": 5})
        away_form_payload = await self._get("/fixtures", {"team": away_id, "last": 5})

        home_form = self._form_score(home_form_payload.get("response", []), home_id)
        away_form = self._form_score(away_form_payload.get("response", []), away_id)

        h2h_edge = 0.0
        h2h_count = 0
        injuries_home = 0
        injuries_away = 0
        market_home, market_draw, market_away = 0.5, 0.28, 0.5
        notes: list[str] = []
        evidence: dict[str, Any] = {
            "provider": "api-football",
            "home_form_last5": round(home_form, 3),
            "away_form_last5": round(away_form, 3),
        }

        try:
            h2h = await self._get(
                "/fixtures/headtohead",
                {"h2h": f"{home_id}-{away_id}", "last": 5},
            )
            h2h_rows = h2h.get("response", [])
            h2h_count = len(h2h_rows)
            h2h_edge = self._h2h_edge(h2h_rows, home_id, away_id)
            evidence["h2h_matches"] = h2h_count
            evidence["h2h_home_edge"] = round(h2h_edge, 3)
        except Exception as exc:
            evidence["h2h"] = f"unavailable: {exc}"

        try:
            injuries = await self._get("/injuries", {"fixture": match_id})
            for row in injuries.get("response", []):
                team_id = int(((row.get("team") or {}).get("id")) or 0)
                if team_id == home_id:
                    injuries_home += 1
                elif team_id == away_id:
                    injuries_away += 1
            evidence["injuries_home"] = injuries_home
            evidence["injuries_away"] = injuries_away
            if injuries_home or injuries_away:
                notes.append(f"伤停：主队{injuries_home}人，客队{injuries_away}人")
        except Exception as exc:
            evidence["injuries"] = f"unavailable: {exc}"

        try:
            odds = await self._get("/odds", {"fixture": match_id})
            parsed = self._match_winner_probabilities(odds.get("response", []))
            if parsed:
                market_home, market_draw, market_away = parsed
                evidence["market_1x2"] = [
                    round(market_home, 3),
                    round(market_draw, 3),
                    round(market_away, 3),
                ]
        except Exception as exc:
            evidence["odds"] = f"unavailable: {exc}"

        squad_home = max(0.45, 0.96 - injuries_home * 0.055)
        squad_away = max(0.45, 0.96 - injuries_away * 0.055)

        match_input = MatchInput(
            match_id=str(fixture.get("id", match_id)),
            home=_safe_name(home_team.get("name")),
            away=_safe_name(away_team.get("name")),
            kickoff=_safe_name(fixture.get("date")),
            competition=_safe_name(league.get("name")),
            home_form=home_form,
            away_form=away_form,
            h2h_home_edge=h2h_edge,
            squad_home=squad_home,
            squad_away=squad_away,
            context_home=0.58,
            context_away=0.42,
            market_home=market_home,
            market_draw=market_draw,
            market_away=market_away,
            notes=notes,
        )
        return match_input, evidence

    @staticmethod
    def _form_score(rows: list[dict], team_id: int) -> float:
        if not rows:
            return 0.5
        points = 0.0
        played = 0
        for row in rows:
            teams = row.get("teams") or {}
            goals = row.get("goals") or {}
            home = teams.get("home") or {}
            away = teams.get("away") or {}
            gh, ga = goals.get("home"), goals.get("away")
            if gh is None or ga is None:
                continue
            played += 1
            if int(home.get("id") or 0) == team_id:
                points += 3 if gh > ga else 1 if gh == ga else 0
            elif int(away.get("id") or 0) == team_id:
                points += 3 if ga > gh else 1 if ga == gh else 0
        return 0.5 if played == 0 else max(0.0, min(1.0, points / (played * 3)))

    @staticmethod
    def _h2h_edge(rows: list[dict], home_id: int, away_id: int) -> float:
        if not rows:
            return 0.0
        edges = []
        for row in rows:
            teams = row.get("teams") or {}
            goals = row.get("goals") or {}
            gh, ga = goals.get("home"), goals.get("away")
            if gh is None or ga is None:
                continue
            row_home = int(((teams.get("home") or {}).get("id")) or 0)
            row_away = int(((teams.get("away") or {}).get("id")) or 0)
            gd = gh - ga
            if row_home == home_id and row_away == away_id:
                edges.append(gd)
            elif row_home == away_id and row_away == home_id:
                edges.append(-gd)
        if not edges:
            return 0.0
        avg = sum(edges) / len(edges)
        return max(-1.0, min(1.0, avg / 3.0))

    @staticmethod
    def _match_winner_probabilities(rows: list[dict]) -> tuple[float, float, float] | None:
        for response in rows:
            for bookmaker in response.get("bookmakers", []):
                for bet in bookmaker.get("bets", []):
                    if str(bet.get("name", "")).lower() not in {"match winner", "1x2"}:
                        continue
                    values = {str(v.get("value", "")).lower(): v.get("odd") for v in bet.get("values", [])}
                    try:
                        h = float(values["home"])
                        d = float(values["draw"])
                        a = float(values["away"])
                    except (KeyError, TypeError, ValueError):
                        continue
                    raw = [1 / h, 1 / d, 1 / a]
                    total = sum(raw)
                    return raw[0] / total, raw[1] / total, raw[2] / total
        return None


football_data_provider = FootballDataProvider()
