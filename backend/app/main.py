from fastapi import Depends, FastAPI, HTTPException

from .models import (
    AnalyzeRequest, AnalyzeResponse, ChatRequest, ChatResponse,
    EvolutionState, ReviewRequest, UserRole,
)
from .security import get_role, require_owner, require_premium
from .services.chat_service import phoenix_chat
from .services.data_provider import football_data_provider
from .services.evolution import evolution_engine
from .services.phoenix_engine import PhoenixEngine

app = FastAPI(title="Phoenix AI 2.0 API", version="2.0.0-mvp")


@app.get("/health")
async def health():
    return {"ok": True, "service": "phoenix-ai2"}


@app.get("/v1/matches/today")
async def matches_today(role: UserRole = Depends(get_role)):
    return await football_data_provider.today()


@app.post("/v1/analyze", response_model=AnalyzeResponse)
async def analyze(body: AnalyzeRequest, role: UserRole = Depends(get_role)):
    engine = PhoenixEngine(weights=evolution_engine.weights)
    predictions = [engine.analyze(match) for match in body.matches]
    if role == UserRole.FREE:
        for p in predictions:
            p.scorelines = p.scorelines[:2]
            p.tail_risks = ["升级 PREMIUM 可查看完整尾部情景与更多比分路径"]
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
        "role": role,
        "features": {
            "basic_analysis": True,
            "full_score_paths": role in {UserRole.PREMIUM, UserRole.OWNER},
            "phoenix_chat": role in {UserRole.PREMIUM, UserRole.OWNER},
            "evolution_console": role == UserRole.OWNER,
            "owner_unlimited": role == UserRole.OWNER,
        },
    }
