from __future__ import annotations

from enum import Enum
from typing import Any
from pydantic import BaseModel, Field


class UserRole(str, Enum):
    OWNER = "OWNER"
    PREMIUM = "PREMIUM"
    FREE = "FREE"


class MatchInput(BaseModel):
    match_id: str
    home: str
    away: str
    kickoff: str | None = None
    competition: str | None = None
    home_form: float = Field(default=0.5, ge=0, le=1)
    away_form: float = Field(default=0.5, ge=0, le=1)
    h2h_home_edge: float = Field(default=0.0, ge=-1, le=1)
    squad_home: float = Field(default=0.5, ge=0, le=1)
    squad_away: float = Field(default=0.5, ge=0, le=1)
    context_home: float = Field(default=0.5, ge=0, le=1)
    context_away: float = Field(default=0.5, ge=0, le=1)
    market_home: float = Field(default=0.5, ge=0, le=1)
    market_draw: float = Field(default=0.25, ge=0, le=1)
    market_away: float = Field(default=0.5, ge=0, le=1)
    notes: list[str] = Field(default_factory=list)


class Prediction(BaseModel):
    match_id: str
    home: str
    away: str
    home_win: float
    draw: float
    away_win: float
    expected_goals_home: float
    expected_goals_away: float
    scorelines: list[dict[str, Any]]
    tail_risks: list[str]
    confidence: float
    explanation: list[str]


class AnalyzeRequest(BaseModel):
    matches: list[MatchInput]


class AnalyzeResponse(BaseModel):
    predictions: list[Prediction]


class ChatRequest(BaseModel):
    message: str
    context: dict[str, Any] | None = None


class ChatResponse(BaseModel):
    reply: str


class ReviewRequest(BaseModel):
    match_id: str
    predicted_home: float
    predicted_draw: float
    predicted_away: float
    actual_result: str
    predicted_score: str | None = None
    actual_score: str | None = None


class EvolutionState(BaseModel):
    version: str
    samples: int
    brier_ema: float
    weights: dict[str, float]
    notes: list[str]
