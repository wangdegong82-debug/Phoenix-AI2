# Phoenix AI 2.0

Phoenix AI 2.0 is an Android + FastAPI starter project for football match research,
simulation, explainable predictions, a Phoenix chat assistant, access tiers, and
auditable post-match evolution.

## Structure
- `android/` native Android app (Kotlin + Jetpack Compose)
- `backend/` FastAPI API
- Phoenix Engine: form / H2H / squad / context / market feature fusion
- Phoenix Chat: server-side LLM assistant
- Evolution: Brier-score review + bounded, reversible weight nudges
- Access tiers: OWNER / PREMIUM / FREE

## Important
Statistical/entertainment analysis only. No result or profit is guaranteed.
For production: use licensed data, real authentication, billing verification,
responsible-gambling controls, rate limits, audit logs and protected secrets.

## Backend
```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Docs: `http://127.0.0.1:8000/docs`

## API
- `GET /health`
- `GET /v1/matches/today`
- `POST /v1/analyze`
- `POST /v1/chat`
- `POST /v1/evolution/review`
- `GET /v1/evolution/state`
- `GET /v1/access/me`

Temporary MVP role header:
`X-User-Role: OWNER`

Never commit real API keys.
