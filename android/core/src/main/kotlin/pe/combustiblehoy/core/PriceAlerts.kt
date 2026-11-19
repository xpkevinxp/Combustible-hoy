package pe.combustiblehoy.core

import java.util.Locale
import kotlin.math.abs

data class PriceChange(
    val stationId: String,
    val stationName: String,
    val fuel: Fuel,
    val oldPrice: Double,
    val newPrice: Double,
) {
    val delta: Double get() = newPrice - oldPrice
    val isDrop: Boolean get() = delta < 0
}

data class FavoriteSnapshot(val stationId: String, val name: String, val prices: Map<String, Double>)

object PriceAlerts {
    /**
     * Compara los precios guardados de los favoritos con los nuevos y devuelve los cambios
     * del combustible elegido cuyo valor absoluto alcanza [threshold] soles.
     */
    fun detect(
        favorites: List<FavoriteSnapshot>,
        latest: Map<String, Map<String, Double>>,
        fuel: Fuel,
        threshold: Double = 0.10,
        onlyDrops: Boolean = false,
    ): List<PriceChange> = favorites.mapNotNull { fav ->
        val old = fav.prices[fuel.code] ?: return@mapNotNull null
        val new = latest[fav.stationId]?.get(fuel.code) ?: return@mapNotNull null
        val change = PriceChange(fav.stationId, fav.name, fuel, old, new)
        when {
            abs(change.delta) + 1e-9 < threshold -> null
            onlyDrops && !change.isDrop -> null
            else -> change
        }
    }.sortedBy { it.delta }

    fun describe(change: PriceChange): String {
        val arrow = if (change.isDrop) "bajó" else "subió"
        return "${change.fuel.label} $arrow ${money(abs(change.delta))} → ${money(change.newPrice)}"
    }

    fun money(value: Double): String = "S/ " + String.format(Locale.US, "%.2f", value)
}
