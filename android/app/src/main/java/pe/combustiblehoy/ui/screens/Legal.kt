package pe.combustiblehoy.ui.screens

import pe.combustiblehoy.BuildConfig

/** Textos legales exigidos por Google Play (la app no es del Estado) y enlaces. */
object Legal {
    const val DISCLAIMER =
        "Combustible Hoy es una app independiente: no está afiliada a Osinergmin, Facilito ni al Estado peruano. " +
            "Muestra datos públicos que publica Osinergmin; los precios son los declarados por cada establecimiento " +
            "y pueden diferir del surtidor."
    val PRIVACY_URL = BuildConfig.DATA_BASE_URL + "privacidad.html"
    const val SOURCE_URL = "https://www.facilito.gob.pe/"
}
