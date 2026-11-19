"""Catálogo de combustibles y mapeo de productos de Osinergmin (SCOP) a códigos cortos."""
from __future__ import annotations

import re
import unicodedata

# Código -> etiqueta legible. El orden es el orden de presentación en la app.
FUELS: dict[str, str] = {
    "GHR": "Gasohol Regular",
    "GHP": "Gasohol Premium",
    "DB5": "Diésel B5",
    "GLP": "GLP",
    "GNV": "GNV",
    "REG": "Gasolina Regular",
    "PREM": "Gasolina Premium",
    "G84": "Gasolina 84",
    "D2": "Diésel 2",
    "GNL": "GNL",
}

# COD_PRODUCTO de Osinergmin -> (código, prioridad). Si un establecimiento reporta dos
# productos que se agrupan en el mismo código, gana el de menor prioridad.
PRODUCT_CODES: dict[str, tuple[str, int]] = {
    "126": ("GHR", 0),   # GASOHOL REGULAR
    "127": ("GHP", 0),   # GASOHOL PREMIUM
    "124": ("REG", 0),   # GASOLINA REGULAR
    "125": ("PREM", 0),  # GASOLINA PREMIUM
    "06": ("G84", 0),    # GASOLINA 84
    "6": ("G84", 0),
    "40": ("DB5", 0),    # Diesel B5 S-50 UV
    "39": ("DB5", 1),    # DIESEL B5 UV
    "45": ("DB5", 2),    # Diesel B5 S-50 (mayoristas)
    "60": ("D2", 0),     # DIESEL 2 UV
    "69": ("D2", 1),     # Diesel 2 S-50 UV
    "49": ("GLP", 0),    # GLP - G (granel)
    "131": ("GNV", 0),   # GAS NATURAL VEHICULAR COMPRIMIDO
    "129": ("GNL", 0),   # GAS NATURAL VEHICULAR LICUEFACTADO
    # "52": Cilindros de 10 Kg de GLP -> no aplica a vehículos, se ignora.
}


def norm(text: object) -> str:
    """Mayúsculas, sin tildes, espacios colapsados."""
    s = unicodedata.normalize("NFKD", str(text or ""))
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", s).strip().upper()


# Respaldo por nombre (si el archivo no trae COD_PRODUCTO). La primera regla que coincide gana.
_RULES: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"CILINDRO"), ""),
    (re.compile(r"LICUEFACTADO|\bGNL\b"), "GNL"),
    (re.compile(r"\bGNV\b|GAS NATURAL"), "GNV"),
    (re.compile(r"\bGLP\b|GAS LICUADO"), "GLP"),
    (re.compile(r"DIESEL 2|\bD2\b"), "D2"),
    (re.compile(r"DIESEL|\bDB5\b"), "DB5"),
    (re.compile(r"GASOHOL.*PREMIUM"), "GHP"),
    (re.compile(r"GASOHOL.*REGULAR"), "GHR"),
    (re.compile(r"PREMIUM"), "PREM"),
    (re.compile(r"REGULAR"), "REG"),
    (re.compile(r"\b84\b"), "G84"),
]


def product_code(raw_name: object, raw_code: object = None) -> tuple[str, int] | None:
    """Devuelve (código, prioridad). Usa COD_PRODUCTO y, si no existe, el nombre."""
    code = str(raw_code or "").strip()
    code = re.sub(r"\.0+$", "", code)
    if code in PRODUCT_CODES:
        return PRODUCT_CODES[code]
    if code and code.lstrip("0") in PRODUCT_CODES:
        return PRODUCT_CODES[code.lstrip("0")]
    n = norm(raw_name).replace("-", " ")
    if not n:
        return None
    if n in FUELS:
        return n, 0
    for pattern, fuel in _RULES:
        if pattern.search(n):
            return (fuel, 5) if fuel else None
    return None
