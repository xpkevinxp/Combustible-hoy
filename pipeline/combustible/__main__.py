"""CLI: python -m combustible [--config config.yaml] [--prices URL|ruta] [--registry URL|ruta] [--site site/]"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import yaml

from .fetch import fetch_dated
from .normalize import geolocate, merge_registry, normalize_prices, read_table
from .publish import publish

HERE = Path(__file__).resolve().parent.parent


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Genera los JSON de precios de combustible para la app.")
    ap.add_argument("--config", default=str(HERE / "config.yaml"))
    ap.add_argument("--prices", help="URL o ruta del archivo de precios (sobrescribe config/env)")
    ap.add_argument("--registry", help="URL o ruta del registro de establecimientos (opcional)")
    ap.add_argument("--site", default="site", help="Directorio de salida (raíz de GitHub Pages)")
    ap.add_argument("--min-stations", type=int, default=int(os.environ.get("MIN_STATIONS", "1")),
                    help="Falla si se obtienen menos establecimientos (evita publicar datos rotos)")
    ap.add_argument("--no-coords", action="store_true", help="No usar data/coordenadas.csv")
    args = ap.parse_args(argv)

    cfg = yaml.safe_load(Path(args.config).read_text(encoding="utf-8"))
    aliases = cfg["columns"]
    src_p = cfg["sources"]["prices"]
    src_r = cfg["sources"].get("registry") or {}

    prices_url = args.prices or os.environ.get("PRICES_URL") or src_p.get("url", "")
    registry_url = args.registry or os.environ.get("REGISTRY_URL") or src_r.get("url", "")
    coords_file = HERE / "data" / "coordenadas.csv"

    print(f"Descargando precios: {prices_url}")
    df = read_table(fetch_dated(prices_url), src_p.get("format", ""), src_p.get("encoding", "utf-8"), src_p.get("delimiter", ""))
    print(f"  {len(df)} filas, columnas: {list(df.columns)}")
    stations = normalize_prices(df, aliases)
    print(f"  {len(stations)} establecimientos con precio")

    if registry_url:
        print(f"Descargando registro: {registry_url}")
        reg = read_table(fetch_dated(registry_url), src_r.get("format", ""), src_r.get("encoding", "utf-8"), src_r.get("delimiter", ""))
        print(f"  {merge_registry(stations, reg, aliases)} establecimientos completados con el registro")

    if coords_file.exists() and not args.no_coords:
        # Coordenadas exactas de Facilito, actualizadas trimestralmente (facilito-coords.yml).
        coords = read_table(coords_file.read_bytes(), "csv")
        print(f"  {merge_registry(stations, coords, aliases)} establecimientos con coordenadas de Facilito")

    print(f"  ubicación: {geolocate(stations)}")

    if len(stations) < args.min_stations:
        print(f"ERROR: solo {len(stations)} establecimientos (< {args.min_stations}). No se publica.", file=sys.stderr)
        return 1

    meta = publish(stations, Path(args.site))
    print(json.dumps({k: meta[k] for k in ("date", "count")}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
