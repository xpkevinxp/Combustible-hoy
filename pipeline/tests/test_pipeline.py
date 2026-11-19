import datetime as dt
import json
from pathlib import Path

import yaml

from combustible.__main__ import main
from combustible.normalize import merge_registry, normalize_prices, parse_date, parse_float, read_file
from combustible.products import product_code
from combustible.publish import publish, slug

ROOT = Path(__file__).resolve().parent.parent
FIX = Path(__file__).resolve().parent / "fixtures"
ALIASES = yaml.safe_load((ROOT / "config.yaml").read_text(encoding="utf-8"))["columns"]


def test_product_codes():
    assert product_code("GASOHOL 90 PLUS") == "G90"
    assert product_code("Gasohol 84 Plus") == "G84"
    assert product_code("GASOHOL 95 PLUS") == "G95"
    assert product_code("GASOHOL 97 PLUS") == "G97"
    assert product_code("DIESEL B5 S-50 UV") == "DB5"
    assert product_code("Diésel B5") == "DB5"
    assert product_code("GASOLINA REGULAR") == "REG"
    assert product_code("GASOLINA PREMIUM") == "PREM"
    assert product_code("GLP - G") == "GLP"
    assert product_code("GNV") == "GNV"
    assert product_code("KEROSENE") is None
    assert product_code("") is None


def test_parsers():
    assert parse_float("15,49") == 15.49
    assert parse_float("S/ 15.49") == 15.49
    assert parse_float("1,234.50") == 1234.5
    assert parse_float("") is None
    assert parse_date("19/11/2026") == "2026-11-19"
    assert parse_date("2026-11-19 08:00:00") == "2026-11-19"


def test_long_format_keeps_latest_and_filters():
    stations = normalize_prices(read_file(FIX / "precios_largo.csv"), ALIASES)
    assert set(stations) == {"12345", "67890"}
    s = stations["12345"]
    assert s.prices == {"G90": 15.39, "DB5": 16.2, "GLP": 7.99}
    assert s.name == "Grifo San Martin"
    assert s.department == "LIMA"
    j = stations["67890"]
    assert j.prices == {"REG": 16.1, "GNV": 1.85}  # kerosene descartado
    assert j.department == "JUNIN"


def test_wide_format():
    stations = normalize_prices(read_file(FIX / "precios_ancho.csv"), ALIASES)
    assert stations["555"].prices == {"G95": 17.5, "G97": 18.2}


def test_registry_merge_adds_coordinates():
    stations = normalize_prices(read_file(FIX / "precios_largo.csv"), ALIASES)
    n = merge_registry(stations, read_file(FIX / "registro.csv"), ALIASES)
    assert n == 2
    assert (stations["12345"].lat, stations["12345"].lon) == (-12.0912, -77.0251)
    assert stations["67890"].lat is None  # longitud 0 fuera de Perú


def test_publish_and_deltas(tmp_path):
    stations = normalize_prices(read_file(FIX / "precios_largo.csv"), ALIASES)
    publish(stations, tmp_path, dt.date(2026, 11, 18))
    stations["12345"].prices["G90"] = 15.19
    meta = publish(stations, tmp_path, dt.date(2026, 11, 19))

    assert meta["count"] == 2
    assert {d["slug"] for d in meta["departments"]} == {"lima", "junin"}
    lima = json.loads((tmp_path / "latest/dept/lima.json").read_text())
    st = lima["stations"][0]
    assert st["id"] == "12345" and st["p"]["G90"] == 15.19
    assert st["d"] == {"G90": -0.2}
    prices = json.loads((tmp_path / "latest/prices.json").read_text())
    assert prices["p"]["67890"]["GNV"] == 1.85
    assert (tmp_path / "history/2026-11-18/prices.json").exists()
    assert (tmp_path / "history/2026-11-19/prices.json").exists()


def test_slug():
    assert slug("MADRE DE DIOS") == "madre-de-dios"
    assert slug("Junín") == "junin"
    assert slug("") == "sin-departamento"


def test_cli(tmp_path):
    rc = main(["--prices", str(FIX / "precios_largo.csv"), "--registry", str(FIX / "registro.csv"), "--site", str(tmp_path)])
    assert rc == 0
    assert json.loads((tmp_path / "latest/meta.json").read_text())["count"] == 2
    assert main(["--prices", str(FIX / "precios_largo.csv"), "--site", str(tmp_path), "--min-stations", "10"]) == 1
