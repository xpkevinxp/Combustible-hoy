"""Coordenadas exactas de grifos desde Facilito (Osinergmin), con un navegador real.

Facilito protege la búsqueda con reCAPTCHA v3, así que se usa Playwright (Chromium) para
abrir la página y elegir cada departamento como lo haría una persona; el JSON que el sitio
carga se captura de la red. Se ejecuta pocas veces (trimestral) y con pausas entre
departamentos: las coordenadas casi no cambian.

Uso:
  python -m combustible.facilito --discover 240000          # vuelca lo que responde el sitio (depuración)
  python -m combustible.facilito --out data/coordenadas.csv # todos los departamentos
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

BASE = "https://www.facilito.gob.pe/facilito/pages/facilito/buscadorEESS.jsp"
DEPARTMENTS = {
    "10000": "AMAZONAS", "20000": "ANCASH", "30000": "APURIMAC", "40000": "AREQUIPA",
    "50000": "AYACUCHO", "60000": "CAJAMARCA", "70000": "CALLAO", "80000": "CUSCO",
    "90000": "HUANCAVELICA", "100000": "HUANUCO", "110000": "ICA", "120000": "JUNIN",
    "130000": "LA LIBERTAD", "140000": "LAMBAYEQUE", "150000": "LIMA", "160000": "LORETO",
    "170000": "MADRE DE DIOS", "180000": "MOQUEGUA", "190000": "PASCO", "200000": "PIURA",
    "210000": "PUNO", "220000": "SAN MARTIN", "230000": "TACNA", "240000": "TUMBES", "250000": "UCAYALI",
}


def _capture_department(page, code: str, wait_s: float = 25.0) -> list[dict]:
    """Abre el buscador, elige el departamento y devuelve las respuestas de la red tras el envío."""
    captured: list[dict] = []

    def on_response(resp):
        try:
            ctype = resp.headers.get("content-type", "")
            if resp.request.resource_type in ("image", "font", "stylesheet", "media"):
                return
            if "facilito.gob.pe" not in resp.url:
                return
            body = resp.text()
            captured.append({"url": resp.url, "status": resp.status, "method": resp.request.method,
                             "ctype": ctype, "post": resp.request.post_data, "body": body})
        except Exception:  # noqa: BLE001 - respuestas sin cuerpo (redirecciones)
            pass

    page.on("response", on_response)
    page.goto(BASE, wait_until="networkidle", timeout=90_000)
    # Espera a que reCAPTCHA haya puesto el token en el formulario.
    page.wait_for_function("document.getElementById('g-recaptcha-response').value.length > 0", timeout=60_000)
    # Igual que hacer clic en el departamento del mapa (el <select> solo se ve en móvil).
    with page.expect_navigation(timeout=90_000):
        page.evaluate("code => makeAction(code)", code)
    page.wait_for_load_state("networkidle", timeout=90_000)
    deadline = time.time() + wait_s
    while time.time() < deadline and not any(_looks_like_json(c["body"]) for c in captured):
        page.wait_for_timeout(1000)
    page.remove_listener("response", on_response)
    captured.append({"url": page.url, "status": 0, "method": "PAGE", "ctype": "text/html", "post": None,
                     "body": page.content()})
    return captured


def _looks_like_json(text: str) -> bool:
    t = (text or "").lstrip()
    return t[:1] in ("[", "{")


def discover(code: str, out_dir: Path) -> None:
    from playwright.sync_api import sync_playwright

    out_dir.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(locale="es-PE")
        try:
            responses = _capture_department(page, code)
        finally:
            browser.close()
    for i, r in enumerate(responses):
        ext = "json" if _looks_like_json(r["body"]) else "html"
        (out_dir / f"{i:02d}.{ext}").write_text(r["body"], encoding="utf-8")
        print(f"[{i:02d}] {r['method']} {r['status']} {r['ctype']} {r['url']} post={r['post']!r} len={len(r['body'])}")
        if ext == "json":
            print("     ", r["body"][:3000].replace("\n", " "))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--discover", metavar="COD_DEP", help="Vuelca las respuestas para un departamento")
    ap.add_argument("--dump", default="facilito-dump", help="Directorio para --discover")
    args = ap.parse_args(argv)
    if args.discover:
        discover(args.discover, Path(args.dump))
        return 0
    ap.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())
