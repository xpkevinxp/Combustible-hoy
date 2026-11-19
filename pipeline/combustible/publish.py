"""Escribe los JSON estáticos que consume la app (publicados en GitHub Pages).

Estructura en `site/`:
  latest/meta.json          resumen: fecha de datos, departamentos, combustibles
  latest/prices.json        {p: {id: {código: precio}}, u: {id: fecha}} de todo el país (alertas/widget)
  latest/dept/<slug>.json   establecimientos de un departamento con precios y variación vs. día anterior
  history/<fecha>/prices.json  snapshot diario (se conservan HISTORY_DAYS días)
"""
from __future__ import annotations

import datetime as dt
import json
import re
import shutil
from pathlib import Path

from .normalize import Station
from .products import FUELS, norm

SCHEMA_VERSION = 1
HISTORY_DAYS = 120


def slug(text: str) -> str:
    s = re.sub(r"[^A-Z0-9]+", "-", norm(text)).strip("-").lower()
    return s or "sin-departamento"


def _dump(path: Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True), encoding="utf-8")


def load_previous_prices(site: Path, today: dt.date) -> dict[str, dict[str, float]]:
    """Precios de referencia para la variación: último snapshot de un día anterior
    (el pipeline corre varias veces al día) o, si no hay, la última publicación."""
    candidates = []
    history = site / "history"
    if history.exists():
        candidates = sorted((d for d in history.iterdir() if d.name < today.isoformat()), reverse=True)
    for f in [c / "prices.json" for c in candidates[:1]] + [site / "latest" / "prices.json"]:
        if f.exists():
            try:
                return json.loads(f.read_text(encoding="utf-8")).get("p", {})
            except (ValueError, OSError):
                continue
    return {}


def _station_json(st: Station, prev: dict[str, float]) -> dict:
    out = {
        "id": st.id,
        "n": st.name or st.company or f"Establecimiento {st.id}",
        "c": st.company if st.company != st.name else "",
        "act": st.activity,
        "a": st.address,
        "dep": st.department,
        "prov": st.province,
        "dist": st.district,
        "p": st.prices,
        "u": max(st.updated.values(), default=""),
    }
    if st.lat is not None and st.lon is not None:
        out["lat"], out["lon"] = st.lat, st.lon
        out["geo"] = st.geo  # "exacta" | "distrito" | "provincia" (aproximada)
    delta = {k: round(v - prev[k], 2) for k, v in st.prices.items() if k in prev and abs(v - prev[k]) >= 0.005}
    if delta:
        out["d"] = delta
    return out


def publish(stations: dict[str, Station], site: Path, today: dt.date | None = None) -> dict:
    today = today or dt.date.today()
    previous = load_previous_prices(site, today)
    # Fecha/hora del registro más reciente del archivo (Osinergmin lo actualiza cada ~6 h).
    data_date = max((max(s.updated.values(), default="") for s in stations.values()), default="") or today.isoformat()

    latest = site / "latest"
    if latest.exists():
        shutil.rmtree(latest)

    by_dept: dict[str, list[dict]] = {}
    names: dict[str, str] = {}
    for st in sorted(stations.values(), key=lambda s: s.id):
        sl = slug(st.department)
        names.setdefault(sl, st.department or "SIN DEPARTAMENTO")
        by_dept.setdefault(sl, []).append(_station_json(st, previous.get(st.id, {})))

    for sl, items in by_dept.items():
        _dump(latest / "dept" / f"{sl}.json", {"v": SCHEMA_VERSION, "date": data_date, "stations": items})

    prices = {
        "v": SCHEMA_VERSION,
        "date": data_date,
        "p": {s.id: s.prices for s in stations.values()},
        "u": {s.id: max(s.updated.values(), default="") for s in stations.values()},
    }
    _dump(latest / "prices.json", prices)
    _dump(site / "history" / today.isoformat() / "prices.json", prices)

    meta = {
        "v": SCHEMA_VERSION,
        "generated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "date": data_date,
        "count": len(stations),
        "fuels": FUELS,
        "departments": [
            {"slug": sl, "name": names[sl], "count": len(items)} for sl, items in sorted(by_dept.items())
        ],
    }
    _dump(latest / "meta.json", meta)
    _prune_history(site / "history", today)
    (site / ".nojekyll").touch()
    return meta


def _prune_history(history: Path, today: dt.date) -> None:
    if not history.exists():
        return
    cutoff = today - dt.timedelta(days=HISTORY_DAYS)
    for d in history.iterdir():
        try:
            if dt.date.fromisoformat(d.name) < cutoff:
                shutil.rmtree(d)
        except ValueError:
            continue
