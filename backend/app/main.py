from __future__ import annotations

from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from .models import (
    AnalyzeRequest,
    AnalyzeResponse,
    ChatRequest,
    ChatResponse,
    EvolutionState,
    ReviewRequest,
    UserRole,
)
from .security import get_role, require_owner, require_premium
from .services.chat_service import phoenix_chat
from .services.data_provider import football_data_provider
from .services.evolution import evolution_engine
from .services.phoenix_engine import PhoenixEngine

VERSION = "2.2.0"

app = FastAPI(title="Phoenix AI API", version=VERSION)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {
        "ok": True,
        "service": "phoenix-ai",
        "version": VERSION,
        "provider": football_data_provider.provider,
    }


@app.get("/v1/matches/today")
async def matches_today(role: UserRole = Depends(get_role)):
    return await football_data_provider.today()


@app.get("/v1/matches/{match_id}/analysis")
async def match_analysis(match_id: str, role: UserRole = Depends(get_role)):
    try:
        match, evidence = await football_data_provider.build_match_input(match_id)
    except (ValueError, HTTPException) as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Data provider error: {exc}") from exc

    engine = PhoenixEngine(weights=evolution_engine.weights)
    prediction = engine.analyze(match)

    if role == UserRole.FREE:
        prediction.scorelines = prediction.scorelines[:3]
        prediction.tail_risks = prediction.tail_risks[:1]

    return {
        "prediction": prediction.model_dump(),
        "evidence": evidence,
        "role": role.value,
    }


@app.post("/v1/analyze", response_model=AnalyzeResponse)
async def analyze(body: AnalyzeRequest, role: UserRole = Depends(get_role)):
    engine = PhoenixEngine(weights=evolution_engine.weights)
    predictions = [engine.analyze(match) for match in body.matches]
    if role == UserRole.FREE:
        for prediction in predictions:
            prediction.scorelines = prediction.scorelines[:3]
            prediction.tail_risks = prediction.tail_risks[:1]
    return AnalyzeResponse(predictions=predictions)


@app.post("/v1/chat", response_model=ChatResponse)
async def chat(body: ChatRequest, role: UserRole = Depends(get_role)):
    require_premium(role)
    return ChatResponse(reply=await phoenix_chat.reply(body.message, body.context))


@app.post("/v1/evolution/review", response_model=EvolutionState)
async def evolution_review(body: ReviewRequest, role: UserRole = Depends(get_role)):
    require_owner(role)
    try:
        return evolution_engine.review(body)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/v1/evolution/state", response_model=EvolutionState)
async def evolution_state(role: UserRole = Depends(get_role)):
    require_premium(role)
    return evolution_engine.state()


@app.get("/v1/access/me")
async def access_me(role: UserRole = Depends(get_role)):
    return {
        "role": role.value,
        "features": {
            "basic_analysis": True,
            "full_score_paths": role in {UserRole.PREMIUM, UserRole.OWNER},
            "phoenix_chat": role in {UserRole.PREMIUM, UserRole.OWNER},
            "evolution_console": role == UserRole.OWNER,
            "owner_unlimited": role == UserRole.OWNER,
        },
    }
