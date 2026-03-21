from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .services.history_search_service import history_search_service

app = FastAPI(title="HistoryBoard H5 API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

BASE_DIR = Path(__file__).resolve().parents[2]
FRONTEND_DIR = BASE_DIR / "frontend"
app.mount("/assets", StaticFiles(directory=FRONTEND_DIR), name="assets")


@app.get("/")
def index() -> FileResponse:
    return FileResponse(FRONTEND_DIR / "index.html")


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/api/history/search")
def search_history(
    q: str = Query(default="", description="关键词、人物、朝代、年份"),
    limit: int = Query(default=12, ge=1, le=30),
) -> dict[str, object]:
    return history_search_service.search(query=q, limit=limit)


@app.get("/api/history/suggest")
def suggest_history(
    q: str = Query(default="", description="自动补全关键词"),
    limit: int = Query(default=12, ge=1, le=30),
) -> dict[str, object]:
    return {
        "query": q,
        "suggestions": history_search_service.suggest(q, limit=limit),
    }


@app.get("/api/history/events/{event_id}")
def get_history_event(event_id: str) -> dict[str, object]:
    detail = history_search_service.event_detail(event_id)
    if detail is None:
        raise HTTPException(status_code=404, detail=f"事件不存在: {event_id}")
    return detail


@app.get("/api/history/timeline")
def get_history_timeline() -> dict[str, object]:
    return history_search_service.timeline()


@app.get("/api/history/discover")
def discover() -> dict[str, object]:
    return {
        "quick_queries": history_search_service.suggestions(limit=12),
        "default_results": history_search_service.search(query="", limit=8),
    }
