"""Descarga de archivos fuente (HTTP o ruta local), con reintentos y soporte ZIP."""
from __future__ import annotations

import io
import time
import zipfile
from pathlib import Path

import requests

USER_AGENT = "combustible-hoy-pipeline/1.0"


def fetch(source: str, retries: int = 4, timeout: int = 120) -> bytes:
    if not source:
        raise ValueError("Fuente vacía: configura la URL en config.yaml o en la variable de entorno.")
    if not source.startswith(("http://", "https://")):
        data = Path(source).read_bytes()
    else:
        last: Exception | None = None
        for attempt in range(retries):
            try:
                r = requests.get(source, timeout=timeout, headers={"User-Agent": USER_AGENT})
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
