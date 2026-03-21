from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PUNCT_PATTERN = re.compile(r"[\s\-_,，。！？!?:：；;()（）【】\[\]《》'\"`]+")
YEAR_PATTERN = re.compile(r"-?\d{2,4}")


@dataclass
class IndexedEvent:
    payload: dict[str, Any]
    searchable_text: str
    normalized_fields: dict[str, str]


class HistorySearchService:
    def __init__(self, base_dir: Path) -> None:
        data_dir = base_dir / "app" / "data"
        self._china_events = self._load_json(data_dir / "china_history_events.json")
        self._world_events = self._load_json(data_dir / "world_history_events.json")
        self._indexed_events = [self._index_event(event) for event in self._china_events]
        self._by_id = {event["id"]: event for event in self._china_events}
        self._suggestions = self._build_suggestions(self._china_events)

    @staticmethod
    def _load_json(path: Path) -> list[dict[str, Any]]:
        return json.loads(path.read_text(encoding="utf-8"))

    @staticmethod
    def _normalize_text(value: str) -> str:
        lowered = value.lower().strip()
        return PUNCT_PATTERN.sub("", lowered)

    def _index_event(self, event: dict[str, Any]) -> IndexedEvent:
        fields = {
            "title": event["title"],
            "dynasty": event.get("dynasty", ""),
            "summary": event.get("summary", ""),
            "impact": event.get("impact", ""),
            "location": event.get("location", ""),
            "category": event.get("category", ""),
            "aliases": " ".join(event.get("aliases", [])),
            "figures": " ".join(event.get("figures", [])),
            "tags": " ".join(event.get("tags", [])),
            "keywords": " ".join(event.get("keywords", [])),
        }
        normalized = {name: self._normalize_text(text) for name, text in fields.items()}
        search_blob = " ".join(normalized.values())
        return IndexedEvent(payload=event, searchable_text=search_blob, normalized_fields=normalized)

    def _build_suggestions(self, events: list[dict[str, Any]]) -> list[str]:
        candidates: set[str] = set()
        for event in events:
            candidates.add(event["title"])
            candidates.add(event.get("dynasty", ""))
            candidates.add(event.get("category", ""))
            candidates.update(event.get("aliases", []))
            candidates.update(event.get("figures", []))
            candidates.update(event.get("tags", []))
            candidates.update(event.get("keywords", []))

        clean = [item for item in candidates if item and len(item) >= 2]
        return sorted(clean, key=lambda item: (len(item), item))[:500]

    @staticmethod
    def _period_label(start_year: int, end_year: int) -> str:
        def to_label(year: int) -> str:
            if year < 0:
                return f"公元前{abs(year)}"
            return f"公元{year}"

        if start_year == end_year:
            return to_label(start_year)
        return f"{to_label(start_year)} - {to_label(end_year)}"

    @staticmethod
    def _extract_years(query: str) -> list[int]:
        years: list[int] = []
        for raw in YEAR_PATTERN.findall(query):
            try:
                years.append(int(raw))
            except ValueError:
                continue
        return years

    @staticmethod
    def _overlap_ratio(query_text: str, target_text: str) -> float:
        if not query_text or not target_text:
            return 0.0
        qset = set(query_text)
        tset = set(target_text)
        if not qset:
            return 0.0
        return len(qset & tset) / len(qset)

    def _score_event(self, indexed: IndexedEvent, query: str, query_norm: str) -> float:
        event = indexed.payload
        score = event.get("importance", 0.5) * 14

        if not query_norm:
            return score

        title_norm = indexed.normalized_fields["title"]
        aliases_norm = indexed.normalized_fields["aliases"]
        keywords_norm = indexed.normalized_fields["keywords"]
        figures_norm = indexed.normalized_fields["figures"]
        dynasty_norm = indexed.normalized_fields["dynasty"]

        if query_norm in title_norm:
            score += 80
        if query_norm in aliases_norm:
            score += 60
        if query_norm in keywords_norm:
            score += 55
        if query_norm in figures_norm:
            score += 52
        if query_norm in dynasty_norm:
            score += 48
        if query_norm in indexed.searchable_text:
            score += 30

        pieces = [chunk for chunk in re.split(r"[\s,，、/]+", query.lower()) if chunk]
        for piece in pieces:
            token = self._normalize_text(piece)
            if len(token) < 2:
                continue
            if token in title_norm:
                score += 18
            elif token in indexed.searchable_text:
                score += 9

        overlap = self._overlap_ratio(query_norm, indexed.searchable_text)
        score += overlap * 20

        for year in self._extract_years(query):
            start_year = int(event["start_year"])
            end_year = int(event.get("end_year", start_year))
            low, high = sorted((start_year, end_year))
            if low <= year <= high:
                score += 70
                continue
            distance = min(abs(year - low), abs(year - high))
            score += max(0, 36 - distance / 10)

        return round(score, 3)

    def _format_event(self, event: dict[str, Any], relevance: float) -> dict[str, Any]:
        start_year = int(event["start_year"])
        end_year = int(event.get("end_year", start_year))
        return {
            "id": event["id"],
            "title": event["title"],
            "dynasty": event.get("dynasty", ""),
            "category": event.get("category", ""),
            "period": self._period_label(start_year, end_year),
            "start_year": start_year,
            "end_year": end_year,
            "location": event.get("location", ""),
            "summary": event.get("summary", ""),
            "impact": event.get("impact", ""),
            "figures": event.get("figures", []),
            "tags": event.get("tags", []),
            "aliases": event.get("aliases", []),
            "relevance": relevance,
        }

    def get_world_context(
        self,
        start_year: int,
        end_year: int,
        *,
        window: int = 90,
        limit: int = 8,
    ) -> list[dict[str, Any]]:
        results: list[tuple[float, dict[str, Any]]] = []
        low, high = sorted((start_year, end_year))
        center = (low + high) / 2

        for world_event in self._world_events:
            world_low = int(world_event["start_year"])
            world_high = int(world_event.get("end_year", world_low))
            intersects = not (world_high < (low - window) or world_low > (high + window))
            if not intersects:
                continue

            world_center = (world_low + world_high) / 2
            distance = abs(center - world_center)
            score = max(0.1, 120 - distance)
            results.append((score, world_event))

        ranked = sorted(results, key=lambda item: (-item[0], item[1]["start_year"]))[:limit]
        return [
            {
                "id": event["id"],
                "title": event["title"],
                "region": event.get("region", "全球"),
                "period": self._period_label(event["start_year"], event.get("end_year", event["start_year"])),
                "start_year": event["start_year"],
                "end_year": event.get("end_year", event["start_year"]),
                "summary": event.get("summary", ""),
            }
            for _, event in ranked
        ]

    def search(self, query: str, limit: int = 12) -> dict[str, Any]:
        clean_query = query.strip()
        query_norm = self._normalize_text(clean_query)

        scored: list[tuple[float, dict[str, Any]]] = []
        for indexed in self._indexed_events:
            score = self._score_event(indexed, clean_query, query_norm)
            if score >= 14:
                scored.append((score, indexed.payload))

        scored.sort(key=lambda item: (-item[0], item[1]["start_year"]))
        fallback_used = False

        if not scored:
            fallback_used = True
            scored = [
                (event.get("importance", 0.5) * 10, event)
                for event in sorted(
                    self._china_events,
                    key=lambda row: (-row.get("importance", 0.0), abs(row.get("start_year", 0))),
                )[:limit]
            ]

        top = scored[:limit]
        results = [self._format_event(event, relevance=score) for score, event in top]

        for item in results[:3]:
            item["world_context"] = self.get_world_context(item["start_year"], item["end_year"], limit=6)

        return {
            "query": clean_query,
            "count": len(results),
            "fallback_used": fallback_used,
            "results": results,
            "suggestions": self.suggest(clean_query, limit=10),
        }

    def suggest(self, query: str, limit: int = 8) -> list[str]:
        clean_query = query.strip()
        if not clean_query:
            return self.suggestions(limit=limit)

        query_norm = self._normalize_text(clean_query)
        matches = [
            candidate
            for candidate in self._suggestions
            if query_norm in self._normalize_text(candidate)
        ]
        return matches[:limit]

    def suggestions(self, limit: int = 12) -> list[str]:
        featured = [
            "秦始皇",
            "丝绸之路",
            "安史之乱",
            "靖康之变",
            "甲午战争",
            "辛亥革命",
            "改革开放",
            "香港回归",
            "WTO",
            "五四运动",
            "鸦片战争",
            "郑和下西洋",
        ]
        mixed = featured + [item for item in self._suggestions if item not in featured]
        return mixed[:limit]

    def event_detail(self, event_id: str) -> dict[str, Any] | None:
        event = self._by_id.get(event_id)
        if event is None:
            return None

        payload = self._format_event(event, relevance=event.get("importance", 0.5) * 100)
        payload["keywords"] = event.get("keywords", [])
        payload["world_context"] = self.get_world_context(payload["start_year"], payload["end_year"], limit=8)
        return payload

    def timeline(self) -> dict[str, Any]:
        events = [
            {
                "id": event["id"],
                "title": event["title"],
                "dynasty": event.get("dynasty", ""),
                "period": self._period_label(event["start_year"], event.get("end_year", event["start_year"])),
                "start_year": event["start_year"],
                "end_year": event.get("end_year", event["start_year"]),
                "category": event.get("category", ""),
            }
            for event in self._china_events
        ]
        return {
            "count": len(events),
            "events": events,
        }


BASE_DIR = Path(__file__).resolve().parents[2]
history_search_service = HistorySearchService(base_dir=BASE_DIR)
