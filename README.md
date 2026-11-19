# Combustible Hoy ⛽🇵🇪

Herramienta para conductores en Perú: un **widget Android** que muestra el precio de tu combustible favorito en tus grifos favoritos, con lista de **grifos cercanos** y **alertas** cuando cambia el precio.

Los datos vienen de los precios que los establecimientos declaran ante **Osinergmin** (la misma fuente de *Facilito*). Un proceso que corre cada 6 horas los descarga, los normaliza y los publica como JSON estáticos en GitHub Pages; la app solo lee esos JSON.

```
Osinergmin (SCOP, Excel) ──► GitHub Actions (cada 6 h) ──► GitHub Pages (JSON) ──► App + widget Android
                                 pipeline/ (Python)          gh-pages                 android/ (Kotlin)
```

## Estructura

| Carpeta | Qué es |
|---|---|
| `pipeline/` | Proceso en Python: descarga el Excel de Osinergmin, normaliza, ubica por distrito y publica JSON. |
| `.github/workflows/daily-prices.yml` | Corre el pipeline cada 6 h y publica en la rama `gh-pages`. |
| `android/core/` | Lógica pura en Kotlin (modelos, distancia, ranking, detección de cambios de precio) con tests. |
| `android/app/` | App Compose + widget Glance + WorkManager (sincronización y notificaciones). |

## Fuente de datos

Osinergmin publica en SCOP dos Excel de **"Últimos precios registrados"**, actualizados ~cada 6 horas:

| Archivo | Qué contiene | ¿Lo usa la app? |
|---|---|---|
| `Ultimos-Precios-Registrados-EVPC.xlsx` | **EVPC = Establecimientos de Venta al Público de Combustibles**: grifos, estaciones de servicio, gasocentros GLP/GNV, grifos rurales y flotantes. ~5.700 establecimientos, ~17.700 filas (una por establecimiento y producto). Es el precio que paga el conductor. | ✅ Sí |
| `Ultimos-precios-registrados-DMAY.xlsx` | **DMAY = Distribuidores Mayoristas** (Petroperú, Repsol, Valero, Numay…): ~500 filas con el precio de venta a otras empresas (grifos, flotas). No es precio al público. | ❌ No (podría servir como referencia mayorista) |

Columnas de ambos: `NRO_REGISTRO, RUC, RAZON, DEPARTAMENTO, PROVINCIA, DISTRITO, DIRECCION, FCHA_REGISTRO, COD_PRODUCTO, PRODUCTO, PRECIO_VENTA, UNIDAD, CODIGO_OSINERG, ACTIVIDAD, COD_ACTIVIDAD, MARCA` (+ `ULT_PRECIO_DIF_CERO, PRODUCTO_ACTIVO` en EVPC).

Cómo se procesa:
- **ID del grifo**: `CODIGO_OSINERG` (estable; `NRO_REGISTRO` cambia cuando se renueva el registro).
- **Nombre**: `MARCA` casi siempre está vacía, así que se usa la razón social (`RAZON`) + dirección.
- **Producto**: se mapea por `COD_PRODUCTO` (ver tabla abajo). Se descartan filas con `PRODUCTO_ACTIVO = NO` y los cilindros de GLP de 10 kg.
- **Fecha**: `FCHA_REGISTRO` es cuándo el grifo declaró ese precio; hay precios de hace meses, por eso la app muestra la antigüedad.
- **Ubicación**: el Excel **no trae coordenadas**. Se ubica cada grifo en la capital de su distrito (`pipeline/data/distritos.csv`, ubigeo INEI): 99,3 % por distrito y el resto por provincia. Las distancias son aproximadas (se muestran con "≈").
- El servidor de Osinergmin responde 403 si la petición no parece de un navegador: el pipeline envía un User-Agent de navegador.
- La URL lleva el año (`.../SCOP-DOCS/2026/...`); el pipeline usa `{year}` y, si el año nuevo aún no existe, prueba el anterior.

| Código | COD_PRODUCTO Osinergmin | Producto | Unidad |
|---|---|---|---|
| `GHR` | 126 | Gasohol Regular | galón |
| `GHP` | 127 | Gasohol Premium | galón |
| `DB5` | 40 (Diesel B5 S-50 UV), 39 (Diesel B5 UV) | Diésel B5 (si hay ambos gana el 40) | galón |
| `GLP` | 49 | GLP – G (granel) | galón |
| `GNV` | 131 | Gas natural vehicular comprimido | m³ |
| `REG` / `PREM` | 124 / 125 | Gasolina Regular / Premium | galón |
| `G84` | 06 | Gasolina 84 | galón |
| `D2` | 60, 69 | Diésel 2 | galón |
| `GNL` | 129 | Gas natural licuefactado | kg |

## Puesta en marcha

### 1. Publicar los datos (GitHub Pages)
1. Ejecuta el workflow **Precios diarios** (*Actions → Precios diarios → Run workflow*). Ya apunta al Excel EVPC de Osinergmin; no hace falta configurar nada. Luego corre solo 4 veces al día.
2. **Settings → Pages**: *Deploy from a branch* → `gh-pages` / `root`.
3. Los datos quedan en `https://xpkevinxp.github.io/Combustible-hoy/latest/meta.json`.

Variables opcionales (*Settings → Secrets and variables → Actions → Variables*):
- `PRICES_URL`: otra URL de precios.
- `REGISTRY_URL`: archivo con coordenadas exactas por `CODIGO_OSINERG` (columnas `LATITUD`, `LONGITUD`); reemplaza la ubicación aproximada.
- `MIN_STATIONS` (por defecto 3000): si llegan menos establecimientos, no se publica (protege contra archivos rotos).

### 2. Compilar la app
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
| `latest/meta.json` | fecha/hora del último registro, nº de establecimientos, combustibles, departamentos |
| `latest/dept/<slug>.json` | grifos de un departamento: nombre, actividad, dirección, coordenadas (`geo`: exacta/distrito/provincia), precios `p`, variación vs. día anterior `d`, fecha de declaración `u` |
| `latest/prices.json` | `p`: `{id: {combustible: precio}}` y `u`: `{id: fecha}` de todo el país (alertas y widget) |
| `history/AAAA-MM-DD/prices.json` | snapshot diario (120 días) |

## Pipeline en local
```bash
cd pipeline
pip install -r requirements.txt
python -m pytest -q
python -m combustible --site ../site          # descarga el Excel real de Osinergmin
python -m combustible --prices tests/fixtures/evpc_muestra.xlsx --site ../site   # con la muestra
```

## Funciones de la app (MVP)
- **Widget "Mis grifos"** (redimensionable): favoritos ordenados por precio del combustible elegido, variación ▲▼ y botón de actualizar.
- **Cercanos**: usa tu ubicación, filtra por radio (2–25 km, distancia aproximada), ordena por precio o distancia, búsqueda por nombre/distrito, botón "Cómo llegar".
- **Alertas**: sincroniza cada ~6 h y notifica si el precio de tus favoritos cambia más que el umbral (o solo cuando baja).

Los precios son los declarados por los establecimientos y pueden diferir del surtidor.

Ubicación de distritos: [ubigeo-peru-aumentado](https://github.com/jmcastagnetto/ubigeo-peru-aumentado) (MIT).
