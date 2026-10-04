from __future__ import annotations

from dataclasses import dataclass, field
from ..models import ReviewRequest, EvolutionState


@dataclass
class EvolutionEngine:
    version: str = "2.0.0-mvp"
    samples: int = 0
    brier_ema: float = 0.25
    weights: dict[str, float] = field(default_factory=lambda: {
        "form": 0.24,
        "squad": 0.20,
        "h2h": 0.10,
        "context": 0.16,
        "market": 0.30,
    })
    notes: list[str] = field(default_factory=list)

    def review(self, item: ReviewRequest) -> EvolutionState:
        actual = {
            "HOME": (1.0, 0.0, 0.0),
            "DRAW": (0.0, 1.0, 0.0),
            "AWAY": (0.0, 0.0, 1.0),
        }.get(item.actual_result.upper())
        if actual is None:
            raise ValueError("actual_result must be HOME, DRAW or AWAY")

        pred = (item.predicted_home, item.predicted_draw, item.predicted_away)
        brier = sum((p - y) ** 2 for p, y in zip(pred, actual)) / 3.0
        self.samples += 1
        alpha = 0.08
        self.brier_ema = (1 - alpha) * self.brier_ema + alpha * brier

        if brier > 0.30:
            shift = min(0.01, max(0.0, self.weights["market"] - 0.22))
            self.weights["market"] -= shift
            self.weights["form"] += shift * 0.4
            self.weights["squad"] += shift * 0.35
            self.weights["context"] += shift * 0.25
            self.notes.append(f"{item.match_id}: calibration miss, bounded rebalance applied")
        else:
            self.notes.append(f"{item.match_id}: review recorded, no weight change")

        self.notes = self.notes[-30:]
        return self.state()

    def state(self) -> EvolutionState:
        return EvolutionState(
            version=self.version,
            samples=self.samples,
            brier_ema=round(self.brier_ema, 4),
            weights={k: round(v, 4) for k, v in self.weights.items()},
            notes=self.notes[-10:],
        )


evolution_engine = EvolutionEngine()
