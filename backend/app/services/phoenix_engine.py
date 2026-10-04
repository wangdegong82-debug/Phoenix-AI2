from __future__ import annotations

import math
from ..models import MatchInput, Prediction


def _sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-x))


def _normalize(values: list[float]) -> list[float]:
    total = sum(max(v, 0.0001) for v in values)
    return [max(v, 0.0001) / total for v in values]


class PhoenixEngine:
    def __init__(self, weights: dict[str, float] | None = None):
        self.weights = weights or {
            "form": 0.24,
            "squad": 0.20,
            "h2h": 0.10,
            "context": 0.16,
            "market": 0.30,
        }

    def analyze(self, match: MatchInput) -> Prediction:
        form_edge = match.home_form - match.away_form
        squad_edge = match.squad_home - match.squad_away
        context_edge = match.context_home - match.context_away
        market_edge = match.market_home - match.market_away

        edge = (
            self.weights["form"] * form_edge
            + self.weights["squad"] * squad_edge
            + self.weights["h2h"] * match.h2h_home_edge
            + self.weights["context"] * context_edge
            + self.weights["market"] * market_edge
        )

        home_raw = _sigmoid(edge * 3.2)
        away_raw = 1.0 - home_raw
        draw_raw = max(0.12, match.market_draw * (1.0 - min(abs(edge), 0.8) * 0.45))
        home_p, draw_p, away_p = _normalize([home_raw, draw_raw, away_raw])

        tempo = 2.35 + 0.85 * (match.home_form + match.away_form - 1.0)
        imbalance = edge * 1.25
        xg_home = max(0.2, tempo / 2 + imbalance)
        xg_away = max(0.2, tempo / 2 - imbalance)

        scores = self._score_grid(xg_home, xg_away)
        confidence = min(0.96, 0.48 + abs(home_p - away_p) * 0.65)

        risks: list[str] = []
        if abs(edge) < 0.10:
            risks.append("平局/一球差路径权重较高")
        if xg_home + xg_away >= 3.2:
            risks.append("大球尾部：3-2 / 2-3 / 4-2 等互攻比分需保留")
        if max(home_p, away_p) >= 0.68 and match.market_draw >= 0.30:
            risks.append("热门陷阱提示：低赔方向仍需防 1-1 / 0-1 类反向路径")
        if not risks:
            risks.append("当前未触发高等级尾部报警，但仍保留赛前信息变动风险")

        explanation = [
            f"综合强弱边际={edge:+.3f}",
            f"状态差={form_edge:+.2f}，阵容差={squad_edge:+.2f}",
            f"场外/主客条件差={context_edge:+.2f}，市场差={market_edge:+.2f}",
            "胜负方向、净胜球与比分分布分开处理，避免把方向优势直接等同于穿盘。",
        ]
        if match.notes:
            explanation.append("人工情报：" + "；".join(match.notes[:4]))

        return Prediction(
            match_id=match.match_id,
            home=match.home,
            away=match.away,
            home_win=round(home_p, 4),
            draw=round(draw_p, 4),
            away_win=round(away_p, 4),
            expected_goals_home=round(xg_home, 2),
            expected_goals_away=round(xg_away, 2),
            scorelines=scores[:6],
            tail_risks=risks,
            confidence=round(confidence, 3),
            explanation=explanation,
        )

    def _score_grid(self, xh: float, xa: float) -> list[dict]:
        def pois(lmbd: float, k: int) -> float:
            return math.exp(-lmbd) * (lmbd ** k) / math.factorial(k)

        rows = []
        for h in range(0, 6):
            for a in range(0, 6):
                p = pois(xh, h) * pois(xa, a)
                rows.append({"score": f"{h}-{a}", "probability": round(p, 4)})
        rows.sort(key=lambda x: x["probability"], reverse=True)
        return rows
