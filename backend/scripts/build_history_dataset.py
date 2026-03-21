from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

RAW_DIR = Path(__file__).resolve().parents[1] / "data" / "raw"
TARGET_DIR = Path(__file__).resolve().parents[1] / "app" / "data"
CHINA_RAW = RAW_DIR / "china_history_seed.json"
WORLD_RAW = RAW_DIR / "world_history_seed.json"
CHINA_OUT = TARGET_DIR / "china_history_events.json"
WORLD_OUT = TARGET_DIR / "world_history_events.json"

TOKEN_PATTERN = re.compile(r"[\s,，。；;、/]+")


def _ensure_list(value: Any) -> list[str]:
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str) and value.strip():
        return [value.strip()]
    return []


def _ensure_object_list(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    return []


def _to_tokens(value: str) -> list[str]:
    return [chunk for chunk in TOKEN_PATTERN.split(value) if len(chunk) >= 2]


def _build_keywords(event: dict[str, Any]) -> list[str]:
    words: set[str] = set()
    for key in ("title", "dynasty", "category", "location", "summary", "impact"):
        text = str(event.get(key, "")).strip()
        if text:
            words.add(text)
            words.update(_to_tokens(text))

    for key in ("aliases", "figures", "tags"):
        for item in _ensure_list(event.get(key)):
            words.add(item)
            words.update(_to_tokens(item))

    return sorted(words, key=lambda item: (-len(item), item))[:40]


def _normalize_event(event: dict[str, Any]) -> dict[str, Any]:
    normalized = {
        "id": str(event["id"]).strip(),
        "title": str(event["title"]).strip(),
        "aliases": _ensure_list(event.get("aliases")),
        "dynasty": str(event.get("dynasty", "")).strip(),
        "start_year": int(event["start_year"]),
        "end_year": int(event.get("end_year", event["start_year"])),
        "location": str(event.get("location", "")).strip(),
        "figures": _ensure_list(event.get("figures")),
        "summary": str(event.get("summary", "")).strip(),
        "impact": str(event.get("impact", "")).strip(),
        "category": str(event.get("category", "")).strip(),
        "tags": _ensure_list(event.get("tags")),
        "importance": float(event.get("importance", 0.5)),
        "audit_status": str(event.get("audit_status", "UNVERIFIED")).strip() or "UNVERIFIED",
        "last_verified_at": str(event.get("last_verified_at", "")).strip(),
        "sources": _ensure_object_list(event.get("sources")),
    }
    normalized["keywords"] = _build_keywords(normalized)
    return normalized


def _normalize_world(event: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": str(event["id"]).strip(),
        "title": str(event["title"]).strip(),
        "region": str(event.get("region", "全球")).strip() or "全球",
        "start_year": int(event["start_year"]),
        "end_year": int(event.get("end_year", event["start_year"])),
        "summary": str(event.get("summary", "")).strip(),
        "audit_status": str(event.get("audit_status", "UNVERIFIED")).strip() or "UNVERIFIED",
        "last_verified_at": str(event.get("last_verified_at", "")).strip(),
        "sources": _ensure_object_list(event.get("sources")),
    }


def _load_json(path: Path) -> list[dict[str, Any]]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, payload: list[dict[str, Any]]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    TARGET_DIR.mkdir(parents=True, exist_ok=True)

    china_events = sorted(
        (_normalize_event(item) for item in _load_json(CHINA_RAW)),
        key=lambda item: (item["start_year"], item["id"]),
    )
    world_events = sorted(
        (_normalize_world(item) for item in _load_json(WORLD_RAW)),
        key=lambda item: (item["start_year"], item["id"]),
    )

    _write_json(CHINA_OUT, china_events)
    _write_json(WORLD_OUT, world_events)

    print(f"Generated {len(china_events)} China events -> {CHINA_OUT}")
    print(f"Generated {len(world_events)} World events -> {WORLD_OUT}")


if __name__ == "__main__":
    main()
