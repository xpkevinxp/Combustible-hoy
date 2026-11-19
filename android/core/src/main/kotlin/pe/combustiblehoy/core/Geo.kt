package pe.combustiblehoy.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Centro aproximado de la capital de cada departamento (slug igual al del pipeline). */
    val departmentCenters: Map<String, LatLon> = mapOf(
        "amazonas" to LatLon(-6.2317, -77.8690),
        "ancash" to LatLon(-9.5278, -77.5278),
        "apurimac" to LatLon(-13.6339, -72.8814),
        "arequipa" to LatLon(-16.3989, -71.5350),
        "ayacucho" to LatLon(-13.1588, -74.2232),
        "cajamarca" to LatLon(-7.1638, -78.5003),
        "callao" to LatLon(-12.0566, -77.1181),
        "cusco" to LatLon(-13.5320, -71.9675),
        "huancavelica" to LatLon(-12.7861, -74.9760),
        "huanuco" to LatLon(-9.9306, -76.2422),
        "ica" to LatLon(-14.0678, -75.7286),
        "junin" to LatLon(-12.0651, -75.2049),
        "la-libertad" to LatLon(-8.1116, -79.0288),
        "lambayeque" to LatLon(-6.7714, -79.8409),
        "lima" to LatLon(-12.0464, -77.0428),
        "loreto" to LatLon(-3.7491, -73.2538),
        "madre-de-dios" to LatLon(-12.5933, -69.1891),
        "moquegua" to LatLon(-17.1956, -70.9353),
        "pasco" to LatLon(-10.6828, -76.2561),
        "piura" to LatLon(-5.1945, -80.6328),
        "puno" to LatLon(-15.8402, -70.0219),
        "san-martin" to LatLon(-6.0342, -76.9717),
        "tacna" to LatLon(-18.0146, -70.2536),
        "tumbes" to LatLon(-3.5669, -80.4515),
        "ucayali" to LatLon(-8.3791, -74.5539),
    )

    /** Departamento cuya capital está más cerca (heurística para elegir qué archivo descargar). */
    fun nearestDepartment(p: LatLon, available: Collection<String> = departmentCenters.keys): String? =
        departmentCenters.filterKeys { it in available }.minByOrNull { distanceKm(p, it.value) }?.key
}
