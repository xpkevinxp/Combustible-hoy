# Combustible Hoy ⛽🇵🇪

Herramienta para conductores en Perú: un **widget Android** que muestra el precio de tu combustible favorito en tus grifos favoritos, con lista de **grifos cercanos** y **alertas** cuando cambia el precio.

Los datos vienen de los precios que los establecimientos declaran ante **Osinergmin** (la misma fuente de *Facilito*), publicados como datos abiertos. Un proceso diario los descarga, los normaliza y los publica como JSON estáticos en GitHub Pages; la app solo lee esos JSON.

```
Osinergmin (datos abiertos) ──► GitHub Actions (diario) ──► GitHub Pages (JSON) ──► App + widget Android
                                 pipeline/ (Python)          gh-pages                 android/ (Kotlin)
```

## Estructura

| Carpeta | Qué es |
|---|---|
| `pipeline/` | Proceso diario en Python: descarga, normaliza (CSV/XLSX/JSON, columnas por alias) y publica JSON. |
| `.github/workflows/daily-prices.yml` | Corre el pipeline a las 8:00 y 17:00 (hora Lima) y publica en la rama `gh-pages`. |
| `android/core/` | Lógica pura en Kotlin (modelos, distancia, ranking, detección de cambios de precio) con tests. |
| `android/app/` | App Compose + widget Glance + WorkManager (sincronización y notificaciones). |

## Puesta en marcha

### 1. Configurar la fuente de datos
1. En [datosabiertos.gob.pe](https://www.datosabiertos.gob.pe) busca los datasets de Osinergmin de precios de combustibles por establecimiento y copia la URL de descarga del recurso (CSV/XLSX).
2. En GitHub: **Settings → Secrets and variables → Actions → Variables** crea:
   - `PRICES_URL` (obligatoria): URL del archivo de precios.
   - `REGISTRY_URL` (opcional): registro de establecimientos (grifos/gasocentros) con dirección y coordenadas; se une por código Osinergmin.
   - `MIN_STATIONS` (opcional, por defecto 100): si se obtienen menos establecimientos, no se publica (protege contra archivos rotos).
3. Si Osinergmin usa un encabezado distinto, añade el alias en `pipeline/config.yaml` → `columns`.

> ⚠️ Algunos datasets diarios de Osinergmin están **anonimizados** (sin código ni nombre del grifo). Para favoritos se necesita un archivo que identifique al establecimiento (código Osinergmin / nombre / dirección). El pipeline falla con un mensaje claro si no encuentra la columna de código.

### 2. Activar GitHub Pages
1. Ejecuta el workflow **Precios diarios** manualmente (*Actions → Precios diarios → Run workflow*). Para probar sin fuente real marca **use_sample**.
2. **Settings → Pages**: *Deploy from a branch* → `gh-pages` / `root`.
3. Los datos quedan en `https://xpkevinxp.github.io/Combustible-hoy/latest/meta.json`.

### 3. Compilar la app
```bash
cd android
./gradlew :app:assembleDebug          # APK en app/build/outputs/apk/debug/
./gradlew :core:test                  # tests de la lógica
./gradlew :app:assembleDebug -PdataBaseUrl=https://otro-host/   # otra URL de datos
```
Requiere JDK 17 y Android SDK 35. El workflow **Android CI** compila y sube el APK como artefacto en cada push.

## Formato de datos publicado

| Archivo | Contenido |
|---|---|
| `latest/meta.json` | fecha de datos, nº de establecimientos, combustibles, departamentos |
| `latest/dept/<slug>.json` | grifos de un departamento: nombre, dirección, coordenadas, precios `p`, variación vs. día anterior `d` |
| `latest/prices.json` | `{id: {combustible: precio}}` de todo el país (para alertas y widget) |
| `history/AAAA-MM-DD/prices.json` | snapshot diario (120 días) |

Códigos de combustible: `REG` Gasolina Regular, `PREM` Gasolina Premium, `G84`, `G90`, `G95`, `G97` Gasohol, `DB5` Diésel B5, `GLP`, `GNV`.

## Pipeline en local
```bash
cd pipeline
pip install -r requirements.txt
python -m pytest -q
python -m combustible --prices tests/fixtures/precios_largo.csv --registry tests/fixtures/registro.csv --site ../site
```

## Funciones de la app (MVP)
- **Widget "Mis grifos"** (redimensionable): favoritos ordenados por precio del combustible elegido, variación ▲▼ y botón de actualizar.
- **Cercanos**: usa tu ubicación, filtra por radio (2–25 km), ordena por precio o distancia, búsqueda por nombre/distrito, botón "Cómo llegar".
- **Alertas**: sincroniza cada ~6 h y notifica si el precio de tus favoritos cambia más que el umbral (o solo cuando baja).

Los precios son los declarados por los establecimientos y pueden diferir del surtidor.
