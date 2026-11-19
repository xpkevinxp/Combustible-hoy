import datetime as dt
import json
from pathlib import Path

from combustible.facilito import load, parse_points, update

# Respuesta real (recortada) de MapaAction.do?method=getPoints
SAMPLE = json.loads(r'''[
 {"codigoOsinergmin":"146787","latitud":-3.55457,"longitud":-80.43576,
  "unidad":"ESTACION DE SERVICIOS CON GASOCENTRO KYR TUMBES","direccion":"CARRETERA PANAMERICANA NORTE KM 1270",
  "productos":[{"producto":"GASOHOL REGULAR","precioVenta":21.0}]},
 {"codigoOsinergmin":"999","latitud":-3.5,"longitud":0,"unidad":"FUERA","direccion":"","productos":[]},
 {"codigoOsinergmin":"777","latitud":-15.8,"longitud":-70.0,"unidad":"GRIFO JACH\\'A  PAMPA","direccion":"x","productos":[]}
]''')


def test_parse_points_filters_and_cleans():
    pts = parse_points(SAMPLE)
    assert set(pts) == {"146787", "777"}  # longitud 0 descartada
    assert pts["146787"]["LATITUD"] == -3.55457
    assert pts["777"]["NOMBRE_COMERCIAL"] == "GRIFO JACH'A PAMPA"


def test_update_merges_and_keeps_missing(tmp_path: Path):
    out = tmp_path / "coordenadas.csv"
    first = parse_points(SAMPLE)
    update(out, min_points=1, today=dt.date(2026, 1, 1), fetched=first)
    second = {"146787": {**first["146787"], "LATITUD": -3.6}}
    stats = update(out, min_points=1, today=dt.date(2026, 4, 1), fetched=second)
    rows = load(out)
    assert stats == {"consultados": 1, "total": 2, "conservados": 1}
    assert rows["146787"]["LATITUD"] == "-3.6" and rows["146787"]["VISTO"] == "2026-04-01"
    assert rows["777"]["VISTO"] == "2026-01-01"  # se conserva aunque no apareció


def test_update_refuses_too_few_points(tmp_path: Path):
    import pytest

    with pytest.raises(RuntimeError):
        update(tmp_path / "c.csv", min_points=10, fetched=parse_points(SAMPLE))
    assert not (tmp_path / "c.csv").exists()
