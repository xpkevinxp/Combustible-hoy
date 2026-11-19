"""Descarga de archivos fuente (HTTP o ruta local), con reintentos y soporte ZIP."""
from __future__ import annotations

import datetime as dt
import io
import time
import zipfile
from pathlib import Path

import requests

# El servidor de Osinergmin responde 403 a clientes que no parecen navegador.
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0 Safari/537.36 combustible-hoy"
)


def expand(url: str, year: int | None = None) -> str:
    """Reemplaza {year} en la URL (las carpetas de SCOP van por año: .../SCOP-DOCS/2026/...)."""
    return url.replace("{year}", str(year or dt.date.today().year))


def fetch_dated(url: str, **kw) -> bytes:
    """Descarga una URL con {year}; si el año actual aún no existe (enero), prueba el anterior."""
    if "{year}" not in url:
        return fetch(url, **kw)
    year = dt.date.today().year
    try:
        return fetch(expand(url, year), **kw)
    except Exception as first:  # noqa: BLE001
        try:
            return fetch(expand(url, year - 1), **kw)
        except Exception:  # noqa: BLE001
            raise first


def fetch(source: str, retries: int = 4, timeout: int = 120) -> bytes:
    if not source:
        raise ValueError("Fuente vacía: configura la URL en config.yaml o en la variable de entorno.")
    if not source.startswith(("http://", "https://")):
        data = Path(source).read_bytes()
    else:
        last: Exception | None = None
        for attempt in range(retries):
            try:
                r = requests.get(source, timeout=timeout, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
                if 400 <= r.status_code < 500:
                    raise RuntimeError(f"HTTP {r.status_code} al descargar {source}")
                r.raise_for_status()
                data = r.content
                break
            except requests.RequestException as e:  # pragma: no cover - red
                last = e
                time.sleep(2 ** attempt * 3)
        else:  # pragma: no cover - red
            raise RuntimeError(f"No se pudo descargar {source}: {last}")
    return _maybe_unzip(data)


def _maybe_unzip(data: bytes) -> bytes:
    if data[:2] != b"PK":
        return data
    try:
        zf = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return data
    names = zf.namelist()
    # Un XLSX también es un ZIP: si contiene [Content_Types].xml no se descomprime.
    if "[Content_Types].xml" in names:
        return data
    tables = [n for n in names if n.lower().endswith((".csv", ".txt", ".json", ".xlsx"))]
    if not tables:
        return data
    biggest = max(tables, key=lambda n: zf.getinfo(n).file_size)
    return zf.read(biggest)
