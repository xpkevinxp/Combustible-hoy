import datetime as dt
import json
from pathlib import Path

import yaml

from combustible.__main__ import main
from combustible.fetch import expand
from combustible.geo import DistrictLocator
from combustible.normalize import geolocate, merge_registry, normalize_prices, parse_date, parse_float, read_file
from combustible.products import product_code
from combustible.publish import publish, slug

ROOT = Path(__file__).resolve().parent.parent
FIX = Path(__file__).resolve().parent / "fixtures"
ALIASES = yaml.safe_load((ROOT / "config.yaml").read_text(encoding="utf-8"))["columns"]
SAMPLE = FIX / "evpc_muestra.xlsx"  # extracto real de Ultimos-Precios-Registrados-EVPC.xlsx


def load_sample():
    return normalize_prices(read_file(SAMPLE), ALIASES)


def test_product_codes_by_osinergmin_code():
    assert product_code("GASOHOL REGULAR", "126") == ("GHR", 0)
    assert product_code("GASOHOL PREMIUM", "127") == ("GHP", 0)
    assert product_code("GASOLINA REGULAR", "124") == ("REG", 0)
    assert product_code("GASOLINA 84", "06") == ("G84", 0)
    assert product_code("Diesel B5 S-50 UV", "40") == ("DB5", 0)
    assert product_code("DIESEL B5 UV", "39") == ("DB5", 1)
    assert product_code("GLP - G", "49") == ("GLP", 0)
    assert product_code("GAS NATURAL VEHICULAR COMPRIMIDO", "131") == ("GNV", 0)
    assert product_code("Cilindros de 10 Kg de GLP", "52") is None


def test_product_codes_by_name_fallback():
    assert product_code("Gasohol Regular")[0] == "GHR"
    assert product_code("Gasohol Premium")[0] == "GHP"
    assert product_code("Diésel B5")[0] == "DB5"
    assert product_code("DIESEL 2 UV")[0] == "D2"
    assert product_code("GAS NATURAL VEHICULAR LICUEFACTADO")[0] == "GNL"
    assert product_code("Cilindros de 10 Kg de GLP") is None
    assert product_code("KEROSENE") is None


def test_parsers():
    assert parse_float("15,49") == 15.49
    assert parse_float("S/ 15.49") == 15.49
    assert parse_float("") is None
    assert parse_date("19/11/2026") == "2026-11-19"
    assert parse_date("2026-11-18 13:33:55") == "2026-11-18T13:33"
    assert expand("x/{year}/y", 2027) == "x/2027/y"


def test_real_sample():
    stations = load_sample()
    assert len(stations) == 6
    lv = stations["100155"]
    assert lv.prices == {"GHR": 22.49, "GHP": 23.99, "GNV": 1.74, "DB5": 26.09, "GLP": 9.49}
    assert lv.name == lv.company and lv.company  # sin marca: nombre = razón social
    assert lv.department == "LIMA" and lv.district == "LA VICTORIA"
    assert lv.activity
    # Callao normalizado
    assert stations["105356"].department == "CALLAO"
    # DB5: gana S-50 UV (40) sobre B5 UV (39) aunque sea más caro
    assert stations["107272"].prices["DB5"] == 27.0
    # PRODUCTO_ACTIVO = NO se descarta
    assert "DB5" not in stations["109598"].prices


def test_geolocation_by_district():
    stations = load_sample()
    stats = geolocate(stations, DistrictLocator())
    assert stats["sin"] == 0
    si = stations["14693"]
    assert si.geo == "distrito" and abs(si.lat - -12.1) < 0.1 and abs(si.lon - -77.03) < 0.1
    assert stations["105356"].geo == "distrito"  # Ventanilla, Callao
    assert stations["110263"].geo == "provincia"  # Kimbiri no está en el ubigeo -> La Convención


def test_registry_exact_coordinates_take_precedence():
    stations = load_sample()
    assert merge_registry(stations, read_file(FIX / "registro.csv"), ALIASES) == 2
    geolocate(stations)
    assert (stations["105356"].lat, stations["105356"].lon, stations["105356"].geo) == (-11.8752, -77.1301, "exacta")
    assert stations["14693"].geo == "distrito"  # longitud 0 inválida -> aproximada


def test_wide_format():
    stations = normalize_prices(read_file(FIX / "precios_ancho.csv"), ALIASES)
    assert stations["555"].prices == {"GHR": 17.5, "GHP": 18.2}


def test_publish_and_deltas(tmp_path):
    stations = load_sample()
    geolocate(stations)
    publish(stations, tmp_path, dt.date(2026, 11, 18))
    stations["14693"].prices["GHR"] = 23.79
    meta = publish(stations, tmp_path, dt.date(2026, 11, 19))

    assert meta["count"] == 6
    assert {d["slug"] for d in meta["departments"]} == {"lima", "callao", "ucayali", "cusco"}
    assert meta["date"].startswith("2026-")
    lima = {s["id"]: s for s in json.loads((tmp_path / "latest/dept/lima.json").read_text())["stations"]}
    st = lima["14693"]
    assert st["p"]["GHR"] == 23.79 and st["d"] == {"GHR": -0.2}
    assert st["geo"] == "distrito" and "lat" in st
    prices = json.loads((tmp_path / "latest/prices.json").read_text())
    assert prices["p"]["100155"]["GNV"] == 1.74
    assert (tmp_path / "history/2026-11-18/prices.json").exists()


def test_slug():
    assert slug("MADRE DE DIOS") == "madre-de-dios"
    assert slug("Junín") == "junin"
    assert slug("") == "sin-departamento"


def test_cli(tmp_path):
    assert main(["--prices", str(SAMPLE), "--site", str(tmp_path)]) == 0
    assert json.loads((tmp_path / "latest/meta.json").read_text())["count"] == 6
    assert main(["--prices", str(SAMPLE), "--site", str(tmp_path), "--min-stations", "10"]) == 1
