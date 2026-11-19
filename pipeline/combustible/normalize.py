"""Lectura tolerante de los archivos de Osinergmin y normalización a un modelo común.

Formato principal: "Últimos precios registrados" de SCOP (Ultimos-Precios-Registrados-EVPC.xlsx),
una fila por (establecimiento, producto):

  NRO_REGISTRO, RUC, RAZON, DEPARTAMENTO, PROVINCIA, DISTRITO, DIRECCION, FCHA_REGISTRO,
  COD_PRODUCTO, PRODUCTO, PRECIO_VENTA, UNIDAD, CODIGO_OSINERG, ACTIVIDAD, COD_ACTIVIDAD,
  MARCA, ULT_PRECIO_DIF_CERO, PRODUCTO_ACTIVO

También acepta formato "ancho" (una columna por producto) y CSV/JSON.
"""
from __future__ import annotations

import csv
import io
import json
import math
import re
from dataclasses import dataclass, field
from pathlib import Path

import pandas as pd

from .geo import DistrictLocator
from .products import norm, product_code

# Rango geográfico aproximado de Perú, para descartar coordenadas basura.
_LAT_RANGE = (-18.6, 0.2)
_LON_RANGE = (-81.5, -68.5)
# Rango de precio razonable por galón / m3 / kg en soles.
_PRICE_RANGE = (0.5, 60.0)
_NO = {"NO", "N", "0", "FALSE"}


@dataclass
class Station:
    id: str
    name: str = ""
    company: str = ""
    ruc: str = ""
    brand: str = ""
    activity: str = ""
    address: str = ""
    department: str = ""
    province: str = ""
    district: str = ""
    lat: float | None = None
    lon: float | None = None
    geo: str = ""  # "exacta" | "distrito" | "provincia" | ""
    prices: dict[str, float] = field(default_factory=dict)
    updated: dict[str, str] = field(default_factory=dict)
    _rank: dict[str, int] = field(default_factory=dict, repr=False)


def _col_key(name: str) -> str:
    return re.sub(r"[^A-Z0-9]", "_", norm(name)).strip("_")


def read_table(data: bytes, fmt: str = "", encoding: str = "utf-8", delimiter: str = "") -> pd.DataFrame:
    fmt = (fmt or "").lower()
    if fmt == "xlsx" or data[:2] == b"PK":
        return pd.read_excel(io.BytesIO(data), dtype=str)
    if fmt == "json" or data.lstrip()[:1] in (b"[", b"{"):
        payload = json.loads(data.decode(encoding or "utf-8"))
        if isinstance(payload, dict):
            payload = next((v for v in payload.values() if isinstance(v, list)), [])
        return pd.DataFrame(payload).astype(str)
    if data.lstrip()[:5].lower() in (b"<html", b"<!doc"):
        raise ValueError("Se recibió HTML en lugar de datos (¿bloqueo del servidor o URL incorrecta?)")
    try:
        text = data.decode(encoding or "utf-8")
    except UnicodeDecodeError:
        text = data.decode("latin-1")
    text = text.lstrip("﻿")
    if not delimiter:
        try:
            delimiter = csv.Sniffer().sniff(text[:20000], delimiters=",;|\t").delimiter
        except csv.Error:
            delimiter = ","
    return pd.read_csv(io.StringIO(text), sep=delimiter, dtype=str, keep_default_na=False)


def resolve_columns(df: pd.DataFrame, aliases: dict[str, list[str]]) -> dict[str, str]:
    """Devuelve {campo_logico: nombre_columna_real} para las columnas encontradas."""
    by_key = {_col_key(c): c for c in df.columns}
    found: dict[str, str] = {}
    for logical, names in aliases.items():
        for alias in names:
            col = by_key.get(_col_key(alias))
            if col is not None:
                found[logical] = col
                break
    return found


def parse_float(value: object) -> float | None:
    if value is None:
        return None
    s = str(value).strip().replace("S/", "").replace(" ", "")
    if not s or s.lower() in {"nan", "none", "null", "-"}:
        return None
    if "," in s and "." not in s:
        s = s.replace(",", ".")
    else:
        s = s.replace(",", "")
    try:
        f = float(s)
    except ValueError:
        return None
    return None if math.isnan(f) else f


def parse_date(value: object) -> str:
    """Fecha y hora en ISO local (AAAA-MM-DDTHH:MM) o AAAA-MM-DD si no trae hora."""
    s = str(value or "").strip()
    if not s or s.lower() in {"nan", "nat", "none"}:
        return ""
    dt = pd.to_datetime(s, errors="coerce", dayfirst=not re.match(r"^\d{4}-", s))
    if pd.isna(dt):
        return ""
    return dt.strftime("%Y-%m-%dT%H:%M") if (dt.hour or dt.minute) else dt.strftime("%Y-%m-%d")


def _clean(value: object) -> str:
    s = str(value or "").strip()
    return "" if s.lower() in {"nan", "none", "null", "nat"} else re.sub(r"\s+", " ", s)


def _station_id(value: object) -> str:
    return re.sub(r"\.0+$", "", _clean(value))  # "12345.0" proveniente de Excel -> "12345"


def clean_department(text: str) -> str:
    n = norm(text)
    return "CALLAO" if "CALLAO" in n else n


def _fill_meta(st: Station, row: pd.Series, cols: dict[str, str]) -> None:
    def g(key: str) -> str:
        return _clean(row[cols[key]]) if key in cols else ""

    st.company = st.company or g("company")
    st.brand = st.brand or g("brand")
    st.name = st.name or g("name") or st.brand or st.company
    st.ruc = st.ruc or g("ruc")
    st.activity = st.activity or g("activity")
    st.address = st.address or g("address")
    st.department = st.department or clean_department(g("department"))
    st.province = st.province or norm(g("province"))
    st.district = st.district or norm(g("district"))
    if st.geo != "exacta" and "lat" in cols and "lon" in cols:
        lat, lon = parse_float(row[cols["lat"]]), parse_float(row[cols["lon"]])
        if lat is not None and lon is not None and _LAT_RANGE[0] <= lat <= _LAT_RANGE[1] and _LON_RANGE[0] <= lon <= _LON_RANGE[1]:
            st.lat, st.lon, st.geo = round(lat, 6), round(lon, 6), "exacta"


def _set_price(st: Station, code: str, rank: int, price: float | None, date: str) -> None:
    if price is None or not (_PRICE_RANGE[0] <= price <= _PRICE_RANGE[1]):
        return
    if code in st.prices:
        prev_rank = st._rank.get(code, 99)
        # Gana el producto de mayor prioridad; a igual prioridad, el registro más reciente.
        if rank > prev_rank or (rank == prev_rank and date < st.updated.get(code, "")):
            return
    st.prices[code] = round(price, 2)
    st.updated[code] = date
    st._rank[code] = rank


def normalize_prices(df: pd.DataFrame, aliases: dict[str, list[str]]) -> dict[str, Station]:
    cols = resolve_columns(df, aliases)
    if "station_id" not in cols:
        raise ValueError(f"No se encontró columna de código de establecimiento. Columnas: {list(df.columns)}")

    long_format = "price" in cols and ("product" in cols or "product_code" in cols)
    wide_cols: dict[str, tuple[str, int]] = {}
    if not long_format:
        known = set(cols.values())
        for c in df.columns:
            if c not in known and (pc := product_code(c)):
                wide_cols[c] = pc
        if not wide_cols:
            raise ValueError(f"No se encontraron columnas de producto/precio. Columnas: {list(df.columns)}")

    stations: dict[str, Station] = {}
    for _, row in df.iterrows():
        sid = _station_id(row[cols["station_id"]])
        if not sid:
            continue
        st = stations.setdefault(sid, Station(id=sid))
        _fill_meta(st, row, cols)
        date = parse_date(row[cols["date"]]) if "date" in cols else ""
        if long_format:
            if "active" in cols and _clean(row[cols["active"]]).upper() in _NO:
                continue
            pc = product_code(
                row[cols["product"]] if "product" in cols else "",
                row[cols["product_code"]] if "product_code" in cols else None,
            )
            if pc:
                _set_price(st, pc[0], pc[1], parse_float(row[cols["price"]]), date)
        else:
            for col, (code, rank) in wide_cols.items():
                _set_price(st, code, rank, parse_float(row[col]), date)

    return {k: v for k, v in stations.items() if v.prices}


def merge_registry(stations: dict[str, Station], registry: pd.DataFrame, aliases: dict[str, list[str]]) -> int:
    """Completa dirección/ubicación/coordenadas desde un registro de establecimientos."""
    cols = resolve_columns(registry, aliases)
    if "station_id" not in cols:
        return 0
    merged = 0
    for _, row in registry.iterrows():
        st = stations.get(_station_id(row[cols["station_id"]]))
        if st is not None:
            _fill_meta(st, row, cols)
            merged += 1
    return merged


def geolocate(stations: dict[str, Station], locator: DistrictLocator | None = None) -> dict[str, int]:
    """Asigna coordenadas aproximadas (capital del distrito/provincia) a quien no tenga exactas."""
    locator = locator or DistrictLocator()
    stats = {"exacta": 0, "distrito": 0, "provincia": 0, "sin": 0}
    for st in stations.values():
        if st.geo == "exacta":
            stats["exacta"] += 1
            continue
        found = locator.locate(st.department, st.province, st.district)
        if found:
            st.lat, st.lon, st.geo = found
            stats[st.geo] += 1
        else:
            stats["sin"] += 1
    return stats


def read_file(path: str | Path, **kw) -> pd.DataFrame:
    return read_table(Path(path).read_bytes(), **kw)
