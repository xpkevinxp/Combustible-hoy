"""Coordenadas exactas y nombre comercial de los grifos, desde el mapa de Facilito (Osinergmin).

El mapa de Facilito pide los puntos a un endpoint JSON público:

  GET /facilito/actions/MapaAction.do?method=getPoints&tipo=LIQ&producto=126
      &latitudeHI=..&longitudeHI=..&latitudeLO=..&longitudeLO=..

que devuelve [{codigoOsinergmin, latitud, longitud, unidad, direccion, productos:[...]}].
Se consulta una vez por producto con un recuadro que cubre todo el Perú. `codigoOsinergmin`
coincide con CODIGO_OSINERG del Excel EVPC.

Las ubicaciones casi no cambian, así que se ejecuta trimestralmente y el resultado se guarda en
data/coordenadas.csv (versionado en el repo). Las estaciones que dejan de aparecer se conservan.

Uso:
  python -m combustible.facilito                  # actualiza data/coordenadas.csv
  python -m combustible.facilito --min-points 3000
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import sys
import time
from pathlib import Path

import requests

from .fetch import USER_AGENT

ENDPOINT = "https://www.facilito.gob.pe/facilito/actions/MapaAction.do"
# Recuadro que cubre todo el Perú.
PERU_BBOX = {"latitudeHI": 0.5, "longitudeHI": -68.0, "latitudeLO": -18.6, "longitudeLO": -81.6}
# (tipo, producto). Varios productos porque cada consulta solo devuelve grifos que venden ese producto.
QUERIES = [
    ("LIQ", "126"),  # Gasohol Regular
    ("LIQ", "127"),  # Gasohol Premium
    ("LIQ", "40"),   # Diesel B5 S-50 UV
    ("LIQ", "39"),   # Diesel B5 UV
    ("LIQ", "124"),  # Gasolina Regular
    ("LIQ", "125"),  # Gasolina Premium
    ("LIQ", "06"),   # Gasolina 84
    ("LIQ", "60"),   # Diesel 2 UV
    ("GNV", "131"),  # GNV
]
# GLP: el mapa de GLP usa otros parámetros (pendiente). Los gasocentros solo-GLP quedan con
# ubicación aproximada por distrito.

OUT = Path(__file__).resolve().parent.parent / "data" / "coordenadas.csv"
FIELDS = ["CODIGO_OSINERG", "LATITUD", "LONGITUD", "NOMBRE_COMERCIAL", "DIRECCION_FACILITO", "VISTO"]
_LAT_RANGE = (-18.6, 0.3)
_LON_RANGE = (-81.6, -68.0)


def fetch_points(tipo: str, producto: str, retries: int = 4) -> list[dict]:
    """Una petición independiente (sin cookies): el servidor guarda el producto en la sesión y,
    si se reutiliza, ignora el parámetro `producto` y repite el resultado anterior."""
    params = {"method": "getPoints", "tipo": tipo, "producto": producto, **PERU_BBOX}
    last: Exception | None = None
    for attempt in range(retries):
        try:
            r = requests.get(ENDPOINT, params=params, timeout=180,
                            headers={"User-Agent": USER_AGENT, "X-Requested-With": "XMLHttpRequest"})
            r.raise_for_status()
            text = r.content.decode("latin-1").strip()  # el servidor responde en ISO-8859-1
            if not text or text == "null":
                return []
            data = json.loads(text)
            return data if isinstance(data, list) else []
        except (requests.RequestException, ValueError) as e:
            last = e
            time.sleep(5 * (attempt + 1))
    raise RuntimeError(f"Facilito getPoints {tipo}/{producto} falló: {last}")


def _text(value: object) -> str:
    return " ".join(str(value or "").replace("\\'", "'").replace('\\"', '"').split())


def parse_points(items: list[dict]) -> dict[str, dict]:
    points: dict[str, dict] = {}
    for it in items:
        code = str(it.get("codigoOsinergmin") or "").strip()
        lat, lon = it.get("latitud"), it.get("longitud")
        if not code or lat is None or lon is None:
            continue
        lat, lon = float(lat), float(lon)
        if not (_LAT_RANGE[0] <= lat <= _LAT_RANGE[1] and _LON_RANGE[0] <= lon <= _LON_RANGE[1]):
            continue
        points[code] = {
            "CODIGO_OSINERG": code,
            "LATITUD": round(lat, 6),
            "LONGITUD": round(lon, 6),
            "NOMBRE_COMERCIAL": _text(it.get("unidad")),
            "DIRECCION_FACILITO": _text(it.get("direccion")),
        }
    return points


def collect(pause_s: float = 3.0) -> dict[str, dict]:
    points: dict[str, dict] = {}
    for tipo, producto in QUERIES:
        items = fetch_points(tipo, producto)
        found = parse_points(items)
        nuevos = len(set(found) - set(points))
        points.update(found)
        print(f"  {tipo}/{producto}: {len(items)} puntos ({nuevos} nuevos)")
        time.sleep(pause_s)
    return points


def load(path: Path) -> dict[str, dict]:
    if not path.exists():
        return {}
    with open(path, encoding="utf-8", newline="") as f:
        return {row["CODIGO_OSINERG"]: row for row in csv.DictReader(f)}


def save(points: dict[str, dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        for code in sorted(points, key=lambda c: (len(c), c)):
            w.writerow({k: points[code].get(k, "") for k in FIELDS})


def update(path: Path = OUT, min_points: int = 3000, today: dt.date | None = None,
           fetched: dict[str, dict] | None = None) -> dict[str, int]:
    """Une lo consultado con lo ya guardado (no se borran grifos que no aparecieron esta vez)."""
    today = today or dt.date.today()
    fetched = collect() if fetched is None else fetched
    if len(fetched) < min_points:
        raise RuntimeError(f"Solo {len(fetched)} puntos (< {min_points}); no se actualiza {path.name}")
    merged = load(path)
    for code, row in fetched.items():
        merged[code] = {**row, "VISTO": today.isoformat()}
    save(merged, path)
    return {"consultados": len(fetched), "total": len(merged), "conservados": len(merged) - len(fetched)}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--min-points", type=int, default=3000, help="No escribe si se obtienen menos puntos")
    args = ap.parse_args(argv)
    print("Consultando mapa de Facilito…")
    print(json.dumps(update(Path(args.out), args.min_points)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
