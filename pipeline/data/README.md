# Datos de referencia

`distritos.csv`: coordenadas de la capital de cada distrito del Perú (ubigeo INEI).
Derivado de [jmcastagnetto/ubigeo-peru-aumentado](https://github.com/jmcastagnetto/ubigeo-peru-aumentado)
(licencia MIT, © 2021 Jesus M. Castagnetto). Se usa para ubicar de forma **aproximada** los
establecimientos, porque el archivo de precios de Osinergmin no trae coordenadas.

`coordenadas.csv`: ubicación **exacta** y nombre comercial de cada establecimiento
(`CODIGO_OSINERG, LATITUD, LONGITUD, NOMBRE_COMERCIAL, DIRECCION_FACILITO, VISTO`), obtenida del
mapa de [Facilito](https://www.facilito.gob.pe) (endpoint `MapaAction.do?method=getPoints`, un
llamado por producto sobre todo el Perú). La actualiza el workflow `facilito-coords.yml` cada
trimestre; los grifos que dejan de aparecer se conservan (columna `VISTO` = última vez visto).
Cubre ~96 % de los grifos del Excel EVPC; el resto (sobre todo gasocentros solo-GLP) usa la
ubicación aproximada de `distritos.csv`.
