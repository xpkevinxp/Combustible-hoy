package pe.combustiblehoy.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Combustibles soportados. `code` coincide con los códigos que publica el pipeline. */
enum class Fuel(val code: String, val label: String, val unit: String) {
    GHR("GHR", "Gasohol Regular", "gal"),
    GHP("GHP", "Gasohol Premium", "gal"),
    DB5("DB5", "Diésel B5", "gal"),
    GLP("GLP", "GLP", "gal"),
    GNV("GNV", "GNV", "m³"),
    REG("REG", "Gasolina Regular", "gal"),
    PREM("PREM", "Gasolina Premium", "gal"),
    G84("G84", "Gasolina 84", "gal"),
    D2("D2", "Diésel 2", "gal"),
    GNL("GNL", "GNL", "kg");

    companion object {
        fun fromCode(code: String?): Fuel? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
data class Department(val slug: String, val name: String, val count: Int = 0)

@Serializable
data class Meta(
    val v: Int = 1,
    val generated: String = "",
    val date: String = "",
    val count: Int = 0,
    val fuels: Map<String, String> = emptyMap(),
    val departments: List<Department> = emptyList(),
)

@Serializable
data class Station(
    val id: String,
    @SerialName("n") val name: String,
    @SerialName("c") val company: String = "",
    @SerialName("act") val activity: String = "",
    @SerialName("a") val address: String = "",
    @SerialName("dep") val department: String = "",
    @SerialName("prov") val province: String = "",
    @SerialName("dist") val district: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    /** Precisión de lat/lon: "exacta", "distrito" o "provincia" (capital aproximada). */
    val geo: String = "",
    @SerialName("p") val prices: Map<String, Double> = emptyMap(),
    @SerialName("d") val deltas: Map<String, Double> = emptyMap(),
    @SerialName("u") val updated: String = "",
) {
    fun price(fuel: Fuel): Double? = prices[fuel.code]
    fun delta(fuel: Fuel): Double? = deltas[fuel.code]
    val location: LatLon? get() = if (lat != null && lon != null) LatLon(lat, lon) else null
    val isLocationApproximate: Boolean get() = geo != "exacta"
}

@Serializable
data class DepartmentFile(val v: Int = 1, val date: String = "", val stations: List<Station> = emptyList())

@Serializable
data class PricesFile(
    val v: Int = 1,
    val date: String = "",
    val p: Map<String, Map<String, Double>> = emptyMap(),
    /** Fecha del último precio declarado por cada establecimiento. */
    val u: Map<String, String> = emptyMap(),
)

object DataJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    fun meta(text: String): Meta = json.decodeFromString(text)
    fun department(text: String): DepartmentFile = json.decodeFromString(text)
    fun prices(text: String): PricesFile = json.decodeFromString(text)
    fun encodePrices(map: Map<String, Double>): String = json.encodeToString(map)
    fun decodePrices(text: String?): Map<String, Double> =
        if (text.isNullOrBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, Double>>(text) }.getOrDefault(emptyMap())
}
