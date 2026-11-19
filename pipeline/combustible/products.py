"""Catálogo de combustibles y mapeo de nombres de producto de Osinergmin a códigos cortos."""
from __future__ import annotations

import re
import unicodedata

# Código -> etiqueta legible. El orden es el orden de presentación en la app.
FUELS: dict[str, str] = {
    "REG": "Gasolina Regular",
    "PREM": "Gasolina Premium",
    "G84": "Gasohol 84",
    "G90": "Gasohol 90",
    "G95": "Gasohol 95",
    "G97": "Gasohol 97",
    "DB5": "Diésel B5",
    "GLP": "GLP",
    "GNV": "GNV",
}


def norm(text: object) -> str:
    """Mayúsculas, sin tildes, espacios colapsados."""
    s = unicodedata.normalize("NFKD", str(text or ""))
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", s).strip().upper()


# Reglas en orden: la primera que coincide gana.
_RULES: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\bGNV\b|GAS NATURAL"), "GNV"),
    (re.compile(r"\bGLP\b|GAS LICUADO"), "GLP"),
    (re.compile(r"DIESEL|DB5|D2\b|B5"), "DB5"),
    (re.compile(r"PREMIUM"), "PREM"),
    (re.compile(r"REGULAR"), "REG"),
    (re.compile(r"\b97\b"), "G97"),
    (re.compile(r"\b95\b"), "G95"),
    (re.compile(r"\b90\b"), "G90"),
    (re.compile(r"\b84\b"), "G84"),
]


def product_code(raw: object) -> str | None:
    """Convierte p.ej. 'GASOHOL 90 PLUS' -> 'G90', 'DIESEL B5 S-50 UV' -> 'DB5'."""
    n = norm(raw).replace("-", " ")
    if not n:
        return None
    if n in FUELS:
        return n
    for pattern, code in _RULES:
        if pattern.search(n):
            return code
    return None
