"""Ubicación aproximada de establecimientos por distrito (Osinergmin no publica coordenadas)."""
from __future__ import annotations

import csv
import re
from pathlib import Path

from .products import norm

DISTRICTS_CSV = Path(__file__).resolve().parent.parent / "data" / "distritos.csv"


def key(text: str) -> str:
    s = norm(text)
    s = s.replace("PROV. CONST. DEL CALLAO", "CALLAO").replace("PROVINCIA CONSTITUCIONAL DEL CALLAO", "CALLAO")
    return re.sub(r"[^A-Z0-9]+", " ", s).strip()


class DistrictLocator:
    """Busca (dep, prov, dist) -> (lat, lon) de la capital del distrito, con respaldos."""

    def __init__(self, path: Path = DISTRICTS_CSV):
        self.full: dict[tuple[str, str, str], tuple[float, float]] = {}
        self.by_dep_dist: dict[tuple[str, str], tuple[float, float]] = {}
        prov_acc: dict[tuple[str, str], list[tuple[float, float]]] = {}
        with open(path, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                dep, prov, dist = key(row["departamento"]), key(row["provincia"]), key(row["distrito"])
                ll = (float(row["lat"]), float(row["lon"]))
                self.full[(dep, prov, dist)] = ll
                self.by_dep_dist.setdefault((dep, dist), ll)
                prov_acc.setdefault((dep, prov), []).append(ll)
                # Capital de provincia: distrito con el mismo nombre que la provincia.
        self.by_prov: dict[tuple[str, str], tuple[float, float]] = {}
        for (dep, prov), points in prov_acc.items():
            capital = self.full.get((dep, prov, prov))
            self.by_prov[(dep, prov)] = capital or (
                round(sum(p[0] for p in points) / len(points), 5),
                round(sum(p[1] for p in points) / len(points), 5),
            )

    def locate(self, department: str, province: str, district: str) -> tuple[float, float, str] | None:
        """Devuelve (lat, lon, precisión) donde precisión es 'distrito' o 'provincia'."""
        dep, prov, dist = key(department), key(province), key(district)
        ll = self.full.get((dep, prov, dist)) or self.by_dep_dist.get((dep, dist))
        if ll:
            return ll[0], ll[1], "distrito"
        ll = self.by_prov.get((dep, prov))
        if ll:
            return ll[0], ll[1], "provincia"
        return None
